package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound STOMP payload for {@code SEND /app/ack}.
 *
 * <p>Recipients send this after persisting a relayed message to device SQLite so the server can
 * delete the corresponding outbox row.</p>
 */
public record DeliveryAckRequest(
	@NotNull UUID messageId,
	@NotNull Long conversationId,
	@NotNull Long recipientId,
	@NotNull Instant ackedAt
) {}
