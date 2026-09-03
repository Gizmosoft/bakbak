package uk.deadcatlab.bakbak.dto.response;

import java.util.UUID;

/** Response for {@code POST /api/attachments/intent} — presigned PUT URL for direct upload. */
public record AttachmentIntentResponse(UUID attachmentId, String uploadUrl) {}
