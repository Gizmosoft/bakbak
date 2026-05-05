package uk.deadcatlab.bakbak.dto.response;

/**
 * Response returned by register/login endpoints.
 */
public record AuthResponse(
	String token,
	UserResponse user
) {}

