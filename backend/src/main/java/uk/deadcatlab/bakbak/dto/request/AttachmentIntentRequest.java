package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/attachments/intent}. */
public record AttachmentIntentRequest(
	@NotNull
	Long conversationId,
	@NotBlank
	@Size(max = 128)
	String mimeType,
	@Min(1)
	long sizeBytes,
	@Size(max = 255)
	String fileName
) {}
