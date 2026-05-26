package uk.deadcatlab.bakbak.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import uk.deadcatlab.bakbak.exception.UnauthorizedException;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Shared helper for resolving the current user id from the security context.
 */
final class ControllerAuthSupport {

	private ControllerAuthSupport() {}

	static long requireCurrentUserId(Authentication authentication, UserService userService) {
		if (authentication == null
			|| !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken) {
			throw new UnauthorizedException("Not authenticated");
		}
		return userService.requireUserIdByUsername(authentication.getName());
	}
}
