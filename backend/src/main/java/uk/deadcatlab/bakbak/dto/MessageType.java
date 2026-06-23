package uk.deadcatlab.bakbak.dto;

/**
 * Discriminator for {@link uk.deadcatlab.bakbak.dto.response.ChatMessageBroadcast} payloads
 * relayed over WebSocket and stored in the device SQLite outbox.
 */
public enum MessageType {
	CHAT,
	ACK,
	DELIVERED,
	SYSTEM
}
