package uk.deadcatlab.bakbak.dto.response;

import java.time.Instant;
import java.util.UUID;
import uk.deadcatlab.bakbak.dto.MessageType;

/**
 * Canonical {@code MessageEnvelope} broadcast on {@code /topic/conversation/{conversationId}},
 * {@code /user/queue/inbox}, and {@code /user/queue/sent}.
 *
 * <p>{@code id} is client-generated (UUID v4) before send; the server sets
 * {@code serverReceivedAt} on ingest. REST history ({@link MessageResponse}) remains on numeric
 * server ids until the local-first migration completes.</p>
 */
public record ChatMessageBroadcast(
	UUID id,
	Long conversationId,
	Long senderId,
	String content,
	Instant sentAt,
	Instant serverReceivedAt,
	MessageType type
) {}
