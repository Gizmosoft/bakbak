package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/conversations}.
 *
 * <p>Idempotent: if a 1:1 conversation already exists between the requesting user and
 * {@code targetUserId}, that conversation is returned instead of creating a new one.</p>
 */
public record CreateConversationRequest(
	@NotNull
	@Positive
	Long targetUserId
) {}
