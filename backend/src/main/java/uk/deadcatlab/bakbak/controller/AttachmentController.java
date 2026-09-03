package uk.deadcatlab.bakbak.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.deadcatlab.bakbak.dto.request.AttachmentIntentRequest;
import uk.deadcatlab.bakbak.dto.response.AttachmentDownloadResponse;
import uk.deadcatlab.bakbak.dto.response.AttachmentIntentResponse;
import uk.deadcatlab.bakbak.service.AttachmentService;
import uk.deadcatlab.bakbak.service.ConversationService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Presigned-URL endpoints for direct-to-storage media uploads and downloads.
 *
 * <p>File bytes never pass through this server — only metadata and signed URLs.</p>
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

	private final AttachmentService attachmentService;
	private final ConversationService conversationService;
	private final UserService userService;

	public AttachmentController(
		AttachmentService attachmentService,
		ConversationService conversationService,
		UserService userService
	) {
		this.attachmentService = attachmentService;
		this.conversationService = conversationService;
		this.userService = userService;
	}

	@PostMapping("/intent")
	public ResponseEntity<AttachmentIntentResponse> createIntent(
		@Valid @RequestBody AttachmentIntentRequest request,
		Authentication authentication
	) {
		long userId = ControllerAuthSupport.requireCurrentUserId(authentication, userService);
		conversationService.requireConversationExists(request.conversationId());
		conversationService.assertParticipant(request.conversationId(), userId);

		AttachmentIntentResponse response = attachmentService.createIntent(
			request.conversationId(),
			userId,
			request.mimeType(),
			request.sizeBytes(),
			request.fileName()
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{id}/download-url")
	public AttachmentDownloadResponse getDownloadUrl(
		@PathVariable java.util.UUID id,
		Authentication authentication
	) {
		long userId = ControllerAuthSupport.requireCurrentUserId(authentication, userService);
		return attachmentService.getDownloadUrl(id, userId);
	}
}
