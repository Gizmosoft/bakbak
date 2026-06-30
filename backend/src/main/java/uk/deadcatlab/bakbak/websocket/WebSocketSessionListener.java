package uk.deadcatlab.bakbak.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import uk.deadcatlab.bakbak.service.MessageService;
import uk.deadcatlab.bakbak.service.PresenceService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Marks users online/offline on STOMP connect/disconnect, drains the outbox to {@code /user/queue/inbox},
 * and broadcasts {@link uk.deadcatlab.bakbak.dto.response.PresenceEvent} updates.
 */
@Component
public class WebSocketSessionListener {

	private final PresenceService presenceService;
	private final MessageService messageService;
	private final UserService userService;

	public WebSocketSessionListener(
		PresenceService presenceService,
		MessageService messageService,
		UserService userService
	) {
		this.presenceService = presenceService;
		this.messageService = messageService;
		this.userService = userService;
	}

	@EventListener
	public void onConnect(SessionConnectEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		Long userId = resolveUserId(accessor);
		if (userId == null) {
			return;
		}
		String sessionId = accessor.getSessionId();
		presenceService.markOnline(userId, sessionId);
		messageService.pushPendingInbox(userId, resolveUsername(accessor));
	}

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		Long userId = resolveUserId(accessor);
		if (userId == null) {
			return;
		}
		presenceService.markOffline(userId, accessor.getSessionId());
	}

	private Long resolveUserId(StompHeaderAccessor accessor) {
		if (accessor.getUser() == null) {
			return null;
		}
		String username = accessor.getUser().getName();
		if (accessor.getUser() instanceof UserDetails userDetails) {
			username = userDetails.getUsername();
		}
		return userService.requireUserIdByUsername(username);
	}

	private static String resolveUsername(StompHeaderAccessor accessor) {
		if (accessor.getUser() instanceof UserDetails userDetails) {
			return userDetails.getUsername();
		}
		return accessor.getUser().getName();
	}
}
