package uk.deadcatlab.bakbak.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import uk.deadcatlab.bakbak.dto.response.ApiErrorResponse;

/**
 * Central REST exception mapping ({@code DESIGN.md} §12.2).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
		MethodArgumentNotValidException ex,
		HttpServletRequest request
	) {
		String message = ex.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(FieldError::getDefaultMessage)
			.orElse("Validation failed");
		return respond(HttpStatus.BAD_REQUEST, "Validation Failed", message, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
		HttpMessageNotReadableException ex,
		HttpServletRequest request
	) {
		return respond(HttpStatus.BAD_REQUEST, "Bad Request", "Malformed request body", request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
		IllegalArgumentException ex,
		HttpServletRequest request
	) {
		return respond(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(
		ResourceNotFoundException ex,
		HttpServletRequest request
	) {
		return respond(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
	}

	@ExceptionHandler({ UnauthorizedException.class, BadCredentialsException.class })
	public ResponseEntity<ApiErrorResponse> handleUnauthorized(
		RuntimeException ex,
		HttpServletRequest request
	) {
		String message = ex instanceof BadCredentialsException
			? "Invalid credentials"
			: ex.getMessage();
		return respond(HttpStatus.UNAUTHORIZED, "Unauthorized", message, request);
	}

	@ExceptionHandler({ ForbiddenException.class, AccessDeniedException.class })
	public ResponseEntity<ApiErrorResponse> handleForbidden(
		RuntimeException ex,
		HttpServletRequest request
	) {
		return respond(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleConflict(
		ConflictException ex,
		HttpServletRequest request
	) {
		return respond(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
		DataIntegrityViolationException ex,
		HttpServletRequest request
	) {
		return respond(
			HttpStatus.CONFLICT,
			"Conflict",
			"Resource already exists or violates a constraint",
			request
		);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiErrorResponse> handleResponseStatus(
		ResponseStatusException ex,
		HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
		return respond(status, status.getReasonPhrase(), message, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		return respond(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Internal Server Error",
			"An unexpected error occurred",
			request
		);
	}

	private static ResponseEntity<ApiErrorResponse> respond(
		HttpStatus status,
		String error,
		String message,
		HttpServletRequest request
	) {
		ApiErrorResponse body = new ApiErrorResponse(
			Instant.now(),
			status.value(),
			error,
			message,
			request.getRequestURI()
		);
		return ResponseEntity.status(status).body(body);
	}
}
