package uk.deadcatlab.bakbak.dto.response;

/**
 * Own published key status for OTPK replenish heuristics.
 */
public record KeyStatusResponse(
	boolean published,
	Integer registrationId,
	long oneTimePreKeysRemaining,
	Integer currentSignedPreKeyId
) {}
