package uk.deadcatlab.bakbak.dto.response;

/**
 * Minimal user info for directory/search results.
 *
 * <p>Intentionally omits email; see {@link UserResponse} for the authenticated profile shape.</p>
 */
public record UserPublicResponse(
	Long id,
	String username,
	String displayName
) {}
