package uk.deadcatlab.bakbak.controller;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.deadcatlab.bakbak.dto.response.PendingMessageResponse;
import uk.deadcatlab.bakbak.model.OutboxMessage;
import uk.deadcatlab.bakbak.service.OutboxService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Bootstrap endpoint for pending offline messages before WebSocket connects.
 */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

	private final OutboxService outboxService;
	private final UserService userService;

	public InboxController(OutboxService outboxService, UserService userService) {
		this.outboxService = outboxService;
		this.userService = userService;
	}

	@GetMapping("/pending")
	public List<PendingMessageResponse> listPending(Authentication authentication) {
		long userId = ControllerAuthSupport.requireCurrentUserId(authentication, userService);
		return outboxService.drainForRecipient(userId).stream()
			.map(InboxController::toResponse)
			.toList();
	}

	private static PendingMessageResponse toResponse(OutboxMessage row) {
		return PendingMessageResponse.fromOutbox(row);
	}
}
