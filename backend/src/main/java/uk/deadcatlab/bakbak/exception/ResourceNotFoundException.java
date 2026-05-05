package uk.deadcatlab.bakbak.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested domain object does not exist.
 *
 * <p>Mapped to HTTP 404 until a central {@code @RestControllerAdvice} is added.</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}
}
