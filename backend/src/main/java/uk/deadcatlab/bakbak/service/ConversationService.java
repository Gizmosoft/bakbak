package uk.deadcatlab.bakbak.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import uk.deadcatlab.bakbak.dto.response.ConversationResponse;
import uk.deadcatlab.bakbak.dto.response.UserPublicResponse;
import uk.deadcatlab.bakbak.exception.ForbiddenException;
import uk.deadcatlab.bakbak.exception.ResourceNotFoundException;
import uk.deadcatlab.bakbak.model.Conversation;
import uk.deadcatlab.bakbak.model.ConversationParticipant;
import uk.deadcatlab.bakbak.model.ConversationParticipantId;
import uk.deadcatlab.bakbak.model.User;
import uk.deadcatlab.bakbak.repository.ConversationParticipantRepository;
import uk.deadcatlab.bakbak.repository.ConversationParticipantRepository.OtherParticipantView;
import uk.deadcatlab.bakbak.repository.ConversationRepository;
import uk.deadcatlab.bakbak.repository.UserRepository;

/**
 * Conversation use-cases backing {@code POST /api/conversations} and {@code GET /api/conversations}.
 *
 * <p>Encapsulates the participant-key invariant ({@code "min:max"}) and the idempotent
 * "get or create" behaviour: the unique constraint on {@code participant_key} is the source of truth,
 * with an in-app fast path to avoid wasted INSERTs for conversations that already exist.</p>
 */
@Service
public class ConversationService {

	/**
	 * Outcome of idempotent {@code getOrCreate}: whether a new row was inserted and the API-facing payload.
	 */
	public record GetOrCreateResult(boolean created, ConversationResponse response) {}

	private final ConversationRepository conversationRepository;
	private final ConversationParticipantRepository participantRepository;
	private final UserRepository userRepository;
	private final TransactionTemplate transactionTemplate;

	public ConversationService(
		ConversationRepository conversationRepository,
		ConversationParticipantRepository participantRepository,
		UserRepository userRepository,
		PlatformTransactionManager transactionManager
	) {
		this.conversationRepository = conversationRepository;
		this.participantRepository = participantRepository;
		this.userRepository = userRepository;
		// Programmatic transaction so we can catch DataIntegrityViolationException OUTSIDE the
		// failed transaction; an @Transactional method would leave us inside a rollback-only tx
		// where even the recovery read would fail.
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * Returns the 1:1 conversation between {@code requesterId} and {@code otherUserId}, creating it
	 * if it does not yet exist.
	 *
	 * <p>The {@code otherUser} field of the returned response is always {@code otherUserId} — i.e. the
	 * conversation is shaped from the requester's perspective.</p>
	 *
	 * @throws IllegalArgumentException  if both ids refer to the same user
	 * @throws ResourceNotFoundException if {@code otherUserId} (or the requester) does not exist
	 */
	public GetOrCreateResult getOrCreate(Long requesterId, Long otherUserId) {
		if (requesterId.equals(otherUserId)) {
			throw new IllegalArgumentException("Cannot start a conversation with yourself");
		}

		User otherUser = userRepository.findById(otherUserId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));

		String key = buildParticipantKey(requesterId, otherUserId);

		// Fast path: conversation already exists — most opens after the first message hit this branch.
		Optional<Conversation> existing = conversationRepository.findByParticipantKey(key);
		if (existing.isPresent()) {
			return new GetOrCreateResult(false, toResponse(existing.get(), otherUser));
		}

		try {
			Conversation created = transactionTemplate.execute(status -> {
				User requester = userRepository.findById(requesterId)
					.orElseThrow(() -> new ResourceNotFoundException("Requesting user not found"));

				Conversation conv = new Conversation();
				conv.setParticipantKey(key);
				// saveAndFlush surfaces a unique-key violation here, while we still control the tx,
				// rather than at implicit commit time.
				Conversation saved = conversationRepository.saveAndFlush(conv);

				participantRepository.save(newParticipant(saved, requester));
				participantRepository.save(newParticipant(saved, otherUser));
				return saved;
			});

			return new GetOrCreateResult(true, toResponse(created, otherUser));
		} catch (DataIntegrityViolationException raceOnUniqueKey) {
			// Concurrent create from another request won the unique constraint — load the winner.
			Conversation winner = conversationRepository.findByParticipantKey(key)
				.orElseThrow(() -> new IllegalStateException(
					"Unique-key violation on conversations but no row found for key " + key));
			return new GetOrCreateResult(
				false,
				toResponse(winner, otherUser));
		}
	}

	/**
	 * Returns conversations the user participates in. Message previews are derived on-device from
	 * SQLite; the server returns participant metadata only.
	 */
	@Transactional(readOnly = true)
	public List<ConversationResponse> listForUser(Long userId) {
		List<Conversation> conversations = conversationRepository.findAllForUser(userId);
		if (conversations.isEmpty()) {
			return List.of();
		}

		List<Long> conversationIds = conversations.stream().map(Conversation::getId).toList();

		Map<Long, OtherParticipantView> otherByConvId = participantRepository
			.findOtherParticipantsByConversationIds(conversationIds, userId)
			.stream()
			.collect(Collectors.toMap(OtherParticipantView::getConversationId, Function.identity()));

		return conversations.stream()
			.map(c -> {
				OtherParticipantView other = otherByConvId.get(c.getId());
				return new ConversationResponse(
					c.getId(),
					other == null ? null : new UserPublicResponse(
						other.getUserId(), other.getUsername(), other.getDisplayName()),
					null,
					null
				);
			})
			.toList();
	}

	/**
	 * Ensures a conversation row exists for {@code conversationId}.
	 *
	 * <p>Call before {@link #assertParticipant(Long, Long)} on endpoints that distinguish
	 * {@code 404} (unknown conversation) from {@code 403} (known conversation, caller not a member).</p>
	 */
	@Transactional(readOnly = true)
	public void requireConversationExists(Long conversationId) {
		conversationRepository.findById(conversationId)
			.orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
	}

	/**
	 * Authorization helper: throws {@link ForbiddenException} if {@code userId} is not a participant
	 * of the given conversation.
	 *
	 * <p>Callers (REST controllers, {@link uk.deadcatlab.bakbak.websocket.WebSocketAuthorizationInterceptor})
	 * should invoke this before exposing any conversation-scoped data.</p>
	 */
	@Transactional(readOnly = true)
	public void assertParticipant(Long conversationId, Long userId) {
		boolean isMember = participantRepository
			.existsById(new ConversationParticipantId(conversationId, userId));
		if (!isMember) {
			throw new ForbiddenException("Not a participant of this conversation");
		}
	}

	@Transactional(readOnly = true)
	public List<Long> findParticipantUserIds(Long conversationId) {
		return participantRepository.findUserIdsByConversationId(conversationId);
	}

	/**
	 * Builds the deterministic {@code "min:max"} key used by the unique constraint on
	 * {@code conversations.participant_key}. Order-independent so {@code (a, b)} and {@code (b, a)}
	 * resolve to the same conversation.
	 */
	static String buildParticipantKey(Long a, Long b) {
		long min = Math.min(a, b);
		long max = Math.max(a, b);
		return min + ":" + max;
	}

	private static ConversationParticipant newParticipant(Conversation conversation, User user) {
		ConversationParticipant p = new ConversationParticipant();
		p.setConversation(conversation);
		p.setUser(user);
		// id is derived in the @PrePersist hook on ConversationParticipant.
		return p;
	}

	private static ConversationResponse toResponse(Conversation conversation, User otherUser) {
		return new ConversationResponse(
			conversation.getId(),
			new UserPublicResponse(otherUser.getId(), otherUser.getUsername(), otherUser.getDisplayName()),
			null,
			null
		);
	}
}
