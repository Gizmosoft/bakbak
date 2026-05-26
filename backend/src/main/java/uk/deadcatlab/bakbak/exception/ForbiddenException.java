package uk.deadcatlab.bakbak.exception;

/**
 * Thrown when an authenticated user attempts an action they are not authorized for
 * (e.g. accessing a conversation they are not a participant of).
 *
 * <p>Mapped to HTTP 403 by {@link GlobalExceptionHandler}.</p>
 */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
