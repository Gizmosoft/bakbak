package uk.deadcatlab.bakbak.exception;

/**
 * Thrown when a protected endpoint is called without a valid authenticated principal.
 */
public class UnauthorizedException extends RuntimeException {

	public UnauthorizedException(String message) {
		super(message);
	}
}
