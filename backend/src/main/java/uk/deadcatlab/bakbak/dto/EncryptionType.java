package uk.deadcatlab.bakbak.dto;

/**
 * Wire/outbox content encoding for chat bodies.
 *
 * <p>{@code NONE} is legacy plaintext. {@code SIGNAL_V1} is opaque Signal Protocol ciphertext;
 * the server treats it as a blind relay payload.</p>
 */
public enum EncryptionType {
	NONE,
	SIGNAL_V1
}
