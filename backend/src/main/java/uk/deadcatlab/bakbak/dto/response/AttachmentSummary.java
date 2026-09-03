package uk.deadcatlab.bakbak.dto.response;

import java.util.UUID;
import uk.deadcatlab.bakbak.model.Attachment;

/** Attachment metadata included in message broadcasts (no presigned URLs). */
public record AttachmentSummary(UUID id, String mimeType, long sizeBytes) {

	public static AttachmentSummary from(Attachment attachment) {
		return new AttachmentSummary(attachment.getId(), attachment.getMimeType(), attachment.getSizeBytes());
	}
}
