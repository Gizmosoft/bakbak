package uk.deadcatlab.bakbak.controller;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.deadcatlab.bakbak.dto.PresenceStatus;
import uk.deadcatlab.bakbak.service.ConversationService;
import uk.deadcatlab.bakbak.service.PresenceService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Conversation-scoped REST endpoints repurposed after server-side message history removal.
 */
@RestController
@RequestMapping("/api/conversations")
public class MessageController {

	private final ConversationService conversationService;
	private final PresenceService presenceService;
	private final UserService userService;

	public MessageController(
		ConversationService conversationService,
		PresenceService presenceService,
		UserService userService
	) {
		this.conversationService = conversationService;
		this.presenceService = presenceService;
		this.userService = userService;
	}

	/**
	 * Presence map for all participants in a conversation (used for online indicators).
	 */
	@GetMapping("/{conversationId}/participants/presence")
	public Map<Long, PresenceStatus> participantPresence(
		@PathVariable Long conversationId,
		Authentication authentication
	) {
		long userId = ControllerAuthSupport.requireCurrentUserId(authentication, userService);
		conversationService.requireConversationExists(conversationId);
		conversationService.assertParticipant(conversationId, userId);
		List<Long> participantIds = conversationService.findParticipantUserIds(conversationId);
		return presenceService.getPresence(participantIds);
	}
}
