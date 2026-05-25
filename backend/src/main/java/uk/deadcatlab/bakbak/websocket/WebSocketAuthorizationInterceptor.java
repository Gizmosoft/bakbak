package uk.deadcatlab.bakbak.websocket;

import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import uk.deadcatlab.bakbak.service.ConversationService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Enforces conversation membership on inbound STOMP {@code SUBSCRIBE} and {@code SEND} frames.
 *
 * <p>Matches {@code DESIGN.md} §8.4 / §9.4: only participants may listen on
 * {@code /topic/conversation/{id}} or send to {@code /app/chat/{id}}. Authentication (who the user is)
 * is established earlier on {@code CONNECT} by {@link WebSocketAuthInterceptor}.</p>
 *
 * <p>Destinations that do not embed a conversation id (e.g. {@code /user/queue/errors}) are not
 * subject to participant checks here.</p>
 */
@Component
public class WebSocketAuthorizationInterceptor implements ChannelInterceptor {

	/** Broker topic for live messages in a 1:1 thread ({@code SUBSCRIBE}). */
	private static final String TOPIC_CONVERSATION_PREFIX = "/topic/conversation/";

	/** Application destination for sending a message ({@code SEND}). */
	private static final String APP_CHAT_PREFIX = "/app/chat/";

	/** User-private destinations; no conversation id to authorize. */
	private static final String USER_DESTINATION_PREFIX = "/user/";

	private final ConversationService conversationService;
	private final UserService userService;

	public WebSocketAuthorizationInterceptor(
		ConversationService conversationService,
		UserService userService
	) {
		this.conversationService = conversationService;
		this.userService = userService;
	}

	/**
	 * Runs before the message is dispatched. For {@code SUBSCRIBE} and {@code SEND}, verifies that
	 * the authenticated principal is a participant of the conversation referenced in the destination.
	 */
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}

		StompCommand command = accessor.getCommand();
		if (!StompCommand.SUBSCRIBE.equals(command) && !StompCommand.SEND.equals(command)) {
			return message;
		}

		String destination = accessor.getDestination();
		if (destination != null && destination.startsWith(USER_DESTINATION_PREFIX)) {
			// e.g. /user/queue/errors — allowed for any authenticated user on their session
			return message;
		}

		Long conversationId = parseConversationId(destination);
		if (conversationId == null) {
			// Not a conversation-scoped destination; no participant rule applies
			return message;
		}

		long userId = requireUserId(accessor.getUser());
		conversationService.assertParticipant(conversationId, userId);
		return message;
	}

	/**
	 * Extracts the conversation id from destinations this app uses for chat.
	 *
	 * @return conversation id, or {@code null} if {@code destination} is not a recognized pattern
	 */
	private static Long parseConversationId(String destination) {
		if (destination == null) {
			return null;
		}
		if (destination.startsWith(TOPIC_CONVERSATION_PREFIX)) {
			return parsePositiveLongId(destination.substring(TOPIC_CONVERSATION_PREFIX.length()));
		}
		if (destination.startsWith(APP_CHAT_PREFIX)) {
			return parsePositiveLongId(destination.substring(APP_CHAT_PREFIX.length()));
		}
		return null;
	}

	/**
	 * Parses the first path segment as a positive {@code long} (stops at {@code '/'} if present).
	 */
	private static Long parsePositiveLongId(String segment) {
		if (segment == null || segment.isEmpty()) {
			return null;
		}
		int slash = segment.indexOf('/');
		String idPart = slash >= 0 ? segment.substring(0, slash) : segment;
		try {
			long id = Long.parseLong(idPart);
			return id > 0 ? id : null;
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * Resolves the numeric user id from the STOMP session {@link Principal} set at {@code CONNECT}.
	 */
	private long requireUserId(Principal principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated on WebSocket session");
		}
		String username = principal.getName();
		if (principal instanceof UserDetails userDetails) {
			username = userDetails.getUsername();
		}
		return userService.requireUserIdByUsername(username);
	}
}
