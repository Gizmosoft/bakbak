package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Full key bundle upload ({@code PUT /api/keys}).
 */
public record PublishKeysRequest(
	@NotNull Integer registrationId,
	@NotBlank @Size(max = 256) String identityKey,
	@NotNull @Valid SignedPreKeyUpload signedPreKey,
	@NotEmpty @Size(max = 200) List<@Valid OneTimePreKeyUpload> oneTimePreKeys
) {
	public record SignedPreKeyUpload(
		@NotNull Integer keyId,
		@NotBlank @Size(max = 256) String publicKey,
		@NotBlank @Size(max = 512) String signature
	) {}

	public record OneTimePreKeyUpload(
		@NotNull Integer keyId,
		@NotBlank @Size(max = 256) String publicKey
	) {}
}
