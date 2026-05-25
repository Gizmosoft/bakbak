package uk.deadcatlab.bakbak.websocket;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import uk.deadcatlab.bakbak.dto.request.SendMessageRequest;
import uk.deadcatlab.bakbak.service.MessageService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * STOMP entry point for sending chat messages.
 *
 * <p>Clients send to {@code /app/chat/{conversationId}} (application prefix {@code /app} is
 * configured in {@link uk.deadcatlab.bakbak.config.WebSocketConfig}). The authenticated user is
 * taken from the {@link Principal} set on {@code CONNECT} by {@link WebSocketAuthInterceptor}.</p>
 *
 * <p>Delegates to {@link MessageService#send} for persistence, {@code last_message_at} updates, and
 * broadcast to {@code /topic/conversation/{conversationId}}. Participant authorization on
 * {@code SEND} is enforced by {@link WebSocketAuthorizationInterceptor} before this method runs.</p>
 */
@Controller
@Validated
public class ChatController {

	private final MessageService messageService;
	private final UserService userService;

	public ChatController(MessageService messageService, UserService userService) {
		this.messageService = messageService;
		this.userService = userService;
	}

	/**
	 * Handles {@code SEND} to {@code /app/chat/{conversationId}} with body {@code {"content":"..."}}.
	 */
	@MessageMapping("/chat/{conversationId}")
	public void sendMessage(
		@DestinationVariable Long conversationId,
		@Valid @Payload SendMessageRequest request,
		Principal principal
	) {
		long senderId = requireUserId(principal);
		messageService.send(conversationId, senderId, request.content());
	}

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
