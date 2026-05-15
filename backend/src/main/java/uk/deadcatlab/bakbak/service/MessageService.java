package uk.deadcatlab.bakbak.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.deadcatlab.bakbak.dto.response.MessageResponse;
import uk.deadcatlab.bakbak.repository.MessageRepository;

/**
 * Message persistence use-cases: history pagination (REST) and, in later phases, send + broadcast.
 */
@Service
public class MessageService {

	private static final int MIN_PAGE_SIZE = 1;
	private static final int MAX_PAGE_SIZE = 100;

	private final MessageRepository messageRepository;

	public MessageService(MessageRepository messageRepository) {
		this.messageRepository = messageRepository;
	}

	/**
	 * Returns up to {@code limit} messages for {@code conversationId}, newest-first within the
	 * selected window, then reordered to chronological ascending (oldest of the batch first).
	 *
	 * <p>Cursor semantics match {@code GET /api/conversations/{id}/messages}:</p>
	 * <ul>
	 *   <li>{@code before == null} — the {@code limit} newest messages in the conversation.</li>
	 *   <li>{@code before != null} — the {@code limit} newest messages strictly older than
	 *       {@code before} ({@code created_at < before}), for stable infinite scroll.</li>
	 * </ul>
	 *
	 * <p>{@code limit} is clamped to 1..100; callers may also enforce defaults at the HTTP layer.</p>
	 *
	 * <p>Authorization (participant check) is enforced by callers — typically the REST controller
	 * invokes {@link ConversationService#assertParticipant(Long, Long)} before this method.</p>
	 */
	@Transactional(readOnly = true)
	public List<MessageResponse> getHistory(Long conversationId, Instant before, int limit) {
		int effectiveLimit = Math.clamp(limit, MIN_PAGE_SIZE, MAX_PAGE_SIZE);
		List<MessageResponse> newestFirst = messageRepository.findHistoryPage(
			conversationId,
			before,
			PageRequest.of(0, effectiveLimit)
		);
		if (newestFirst.isEmpty()) {
			return List.of();
		}
		ArrayList<MessageResponse> chronological = new ArrayList<>(newestFirst);
		Collections.reverse(chronological);
		return chronological;
	}
}
