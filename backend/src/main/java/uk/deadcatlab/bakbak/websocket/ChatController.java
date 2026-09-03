package uk.deadcatlab.bakbak.websocket;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import uk.deadcatlab.bakbak.dto.request.DeliveryAckRequest;
import uk.deadcatlab.bakbak.dto.request.SendMessageRequest;
import uk.deadcatlab.bakbak.service.MessageService;
import uk.deadcatlab.bakbak.service.PresenceService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * STOMP entry points for chat relay, delivery ACKs, and presence heartbeats.
 *
 * <p>Participant authorization on conversation-scoped {@code SEND} frames is enforced by
 * {@link WebSocketAuthorizationInterceptor} before handler methods run.</p>
 */
@Controller
@Validated
public class ChatController {

	private final MessageService messageService;
	private final PresenceService presenceService;
	private final UserService userService;

	public ChatController(
		MessageService messageService,
		PresenceService presenceService,
		UserService userService
	) {
		this.messageService = messageService;
		this.presenceService = presenceService;
		this.userService = userService;
	}

	/**
	 * Handles {@code SEND} to {@code /app/chat/{conversationId}}.
	 */
	@MessageMapping("/chat/{conversationId}")
	public void sendMessage(
		@org.springframework.messaging.handler.annotation.DestinationVariable Long conversationId,
		@Valid @Payload SendMessageRequest request,
		Principal principal
	) {
		long senderId = requireUserId(principal);
		messageService.send(
			conversationId,
			senderId,
			request.id(),
			request.content(),
			request.encryption(),
			request.attachmentId()
		);
	}

	@MessageMapping("/ack")
	public void acknowledge(@Valid @Payload DeliveryAckRequest request, Principal principal) {
		long userId = requireUserId(principal);
		messageService.acknowledgeDeliveryAsUser(
			request.messageId(),
			request.conversationId(),
			request.recipientId(),
			userId,
			request.ackedAt()
		);
	}

	@MessageMapping("/presence/ping")
	public void presencePing(Principal principal) {
		presenceService.heartbeat(requireUserId(principal));
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
