package uk.deadcatlab.bakbak.dto.response;

/**
 * Public user profile returned to clients.
 *
 * <p>Intentionally does not include email/password unless explicitly required by an endpoint.</p>
 */
public record UserResponse(
	Long id,
	String username,
	String email,
	String displayName
) {}

