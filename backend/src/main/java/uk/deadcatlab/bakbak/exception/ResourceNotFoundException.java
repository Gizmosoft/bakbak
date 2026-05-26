package uk.deadcatlab.bakbak.exception;

/**
 * Thrown when a requested domain object does not exist.
 *
 * <p>Mapped to HTTP 404 by {@link GlobalExceptionHandler}.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}
}
