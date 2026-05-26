package uk.deadcatlab.bakbak.controller;

import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.deadcatlab.bakbak.dto.response.MessageResponse;
import uk.deadcatlab.bakbak.service.ConversationService;
import uk.deadcatlab.bakbak.service.MessageService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Authenticated message history for a single conversation.
 *
 * <p>Requires a valid JWT; paths are protected by default in {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/conversations")
public class MessageController {

	private static final int DEFAULT_MESSAGE_LIMIT = 50;
	private static final int MAX_MESSAGE_LIMIT = 100;

	private final MessageService messageService;
	private final ConversationService conversationService;
	private final UserService userService;

	public MessageController(
		MessageService messageService,
		ConversationService conversationService,
		UserService userService
	) {
		this.messageService = messageService;
		this.conversationService = conversationService;
		this.userService = userService;
	}

	/**
	 * Cursor-paginated message history for {@code conversationId}.
	 *
	 * <p>Query {@code before} is optional (ISO-8601 instant); omit for the newest page. {@code limit}
	 * defaults to 50 and is clamped to 1..100.</p>
	 */
	@GetMapping("/{conversationId}/messages")
	public List<MessageResponse> listMessages(
		@PathVariable Long conversationId,
		@RequestParam(required = false) Instant before,
		@RequestParam(required = false) Integer limit,
		Authentication authentication
	) {
		long userId = ControllerAuthSupport.requireCurrentUserId(authentication, userService);
		conversationService.requireConversationExists(conversationId);
		conversationService.assertParticipant(conversationId, userId);
		int effectiveLimit = limit == null ? DEFAULT_MESSAGE_LIMIT : limit;
		effectiveLimit = Math.clamp(effectiveLimit, 1, MAX_MESSAGE_LIMIT);
		return messageService.getHistory(conversationId, before, effectiveLimit);
	}
}
