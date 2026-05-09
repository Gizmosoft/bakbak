package uk.deadcatlab.bakbak.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an authenticated user attempts an action they are not authorized for
 * (e.g. accessing a conversation they are not a participant of).
 *
 * <p>Mapped to HTTP 403 until a central {@code @RestControllerAdvice} is added.</p>
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
