package uk.deadcatlab.bakbak.exception;

/**
 * Thrown when an action conflicts with existing data (e.g. username or email already registered).
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
