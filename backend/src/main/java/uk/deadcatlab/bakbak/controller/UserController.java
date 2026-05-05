package uk.deadcatlab.bakbak.controller;

import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import uk.deadcatlab.bakbak.dto.response.UserPublicResponse;
import uk.deadcatlab.bakbak.dto.response.UserResponse;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Authenticated user profile and username search.
 *
 * <p>Requires a valid JWT; paths are protected by default in {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

	private static final int DEFAULT_SEARCH_LIMIT = 20;
	private static final int MAX_SEARCH_LIMIT = 100;

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	public UserResponse me(Authentication authentication) {
		long userId = requireCurrentUserId(authentication);
		return userService.getCurrentUser(userId);
	}

	@GetMapping("/search")
	public List<UserPublicResponse> search(
		@RequestParam(name = "q", required = false) String q,
		@RequestParam(required = false) Integer limit,
		Authentication authentication
	) {
		long userId = requireCurrentUserId(authentication);
		int effectiveLimit = limit == null ? DEFAULT_SEARCH_LIMIT : limit;
		effectiveLimit = Math.clamp(effectiveLimit, 1, MAX_SEARCH_LIMIT);
		return userService.searchByUsername(q, userId, effectiveLimit);
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
