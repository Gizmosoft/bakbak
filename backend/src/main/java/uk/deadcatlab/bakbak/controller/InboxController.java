package uk.deadcatlab.bakbak.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.deadcatlab.bakbak.dto.response.AttachmentSummary;
import uk.deadcatlab.bakbak.dto.response.PendingMessageResponse;
import uk.deadcatlab.bakbak.model.OutboxMessage;
import uk.deadcatlab.bakbak.service.AttachmentService;
import uk.deadcatlab.bakbak.service.OutboxService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Bootstrap endpoint for pending offline messages before WebSocket connects.
 */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

	private final OutboxService outboxService;
	private final AttachmentService attachmentService;
	private final UserService userService;

	public InboxController(
		OutboxService outboxService,
		AttachmentService attachmentService,
		UserService userService
	) {
		this.outboxService = outboxService;
		this.attachmentService = attachmentService;
		this.userService = userService;
	}

	@GetMapping("/pending")
	public List<PendingMessageResponse> listPending(Authentication authentication) {
		long userId = ControllerAuthSupport.requireCurrentUserId(authentication, userService);
		return outboxService.listPendingForRecipient(userId).stream()
			.map(this::toResponse)
			.toList();
	}

	private PendingMessageResponse toResponse(OutboxMessage row) {
		AttachmentSummary attachment = resolveAttachment(row.getAttachmentId());
		return PendingMessageResponse.fromOutbox(row, attachment);
	}

	private AttachmentSummary resolveAttachment(UUID attachmentId) {
		if (attachmentId == null) {
			return null;
		}
		return attachmentService.loadSummary(attachmentId);
	}
}
