package uk.deadcatlab.bakbak.dto.response;

import java.time.Instant;

/**
 * Standard API error body ({@code DESIGN.md} §12.1).
 */
public record ApiErrorResponse(
	Instant timestamp,
	int status,
	String error,
	String message,
	String path
) {}
