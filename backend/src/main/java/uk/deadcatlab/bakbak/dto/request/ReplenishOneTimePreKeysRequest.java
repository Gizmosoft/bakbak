package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import uk.deadcatlab.bakbak.dto.request.PublishKeysRequest.OneTimePreKeyUpload;

/**
 * Replenish one-time prekeys ({@code POST /api/keys/onetime}).
 */
public record ReplenishOneTimePreKeysRequest(
	@NotEmpty @Size(max = 200) List<@Valid OneTimePreKeyUpload> oneTimePreKeys
) {}
