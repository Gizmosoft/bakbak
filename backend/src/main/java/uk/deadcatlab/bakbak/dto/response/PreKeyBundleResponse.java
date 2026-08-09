package uk.deadcatlab.bakbak.dto.response;

/**
 * Prekey bundle returned for X3DH session establishment.
 *
 * <p>{@code oneTimePreKey} may be null when the recipient has no unused OTPKs.</p>
 */
public record PreKeyBundleResponse(
	Long userId,
	Integer registrationId,
	String identityKey,
	SignedPreKeyPublic signedPreKey,
	OneTimePreKeyPublic oneTimePreKey
) {
	public record SignedPreKeyPublic(Integer keyId, String publicKey, String signature) {}

	public record OneTimePreKeyPublic(Integer keyId, String publicKey) {}
}
