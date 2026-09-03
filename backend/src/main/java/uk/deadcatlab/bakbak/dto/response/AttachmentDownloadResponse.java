package uk.deadcatlab.bakbak.dto.response;

import java.util.UUID;

/** Response for {@code GET /api/attachments/{id}/download-url} — fresh presigned GET URL. */
public record AttachmentDownloadResponse(
	UUID attachmentId,
	String downloadUrl,
	String mimeType,
	long sizeBytes
) {}
