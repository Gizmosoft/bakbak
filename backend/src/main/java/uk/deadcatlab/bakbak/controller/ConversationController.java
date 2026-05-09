package uk.deadcatlab.bakbak.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.deadcatlab.bakbak.dto.request.CreateConversationRequest;
import uk.deadcatlab.bakbak.dto.response.ConversationResponse;
import uk.deadcatlab.bakbak.service.ConversationService;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Authenticated conversation endpoints: create-or-fetch 1:1 thread and list contact window.
 *
 * <p>Requires a valid JWT; paths are protected by default in {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

	private final ConversationService conversationService;
	private final UserService userService;

	public ConversationController(ConversationService conversationService, UserService userService) {
		this.conversationService = conversationService;
		this.userService = userService;
	}

	/**
	 * Idempotent create: returns {@code 201 Created} when a new conversation row is inserted,
	 * {@code 200 OK} when it already existed (including after a concurrent race).
	 */
	@PostMapping
	public ResponseEntity<ConversationResponse> createOrFetch(
		@Valid @RequestBody CreateConversationRequest request,
		Authentication authentication
	) {
		long userId = requireCurrentUserId(authentication);
		try {
			ConversationService.GetOrCreateResult result =
				conversationService.getOrCreate(userId, request.targetUserId());
			HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
			return ResponseEntity.status(status).body(result.response());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	@GetMapping
	public List<ConversationResponse> list(Authentication authentication) {
		long userId = requireCurrentUserId(authentication);
		return conversationService.listForUser(userId);
	}

	private long requireCurrentUserId(Authentication authentication) {
		if (authentication == null
			|| !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
		}
		return userService.requireUserIdByUsername(authentication.getName());
	}
}
