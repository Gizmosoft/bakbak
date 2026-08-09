package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Rotate the current signed prekey ({@code POST /api/keys/signed-prekey}).
 */
public record RotateSignedPreKeyRequest(
	@NotNull Integer keyId,
	@NotBlank @Size(max = 256) String publicKey,
	@NotBlank @Size(max = 512) String signature
) {}
