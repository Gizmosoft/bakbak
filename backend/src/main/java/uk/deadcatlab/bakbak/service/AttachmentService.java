package uk.deadcatlab.bakbak.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.deadcatlab.bakbak.config.StorageProperties;
import uk.deadcatlab.bakbak.dto.AttachmentStatus;
import uk.deadcatlab.bakbak.dto.response.AttachmentDownloadResponse;
import uk.deadcatlab.bakbak.dto.response.AttachmentIntentResponse;
import uk.deadcatlab.bakbak.dto.response.AttachmentSummary;
import uk.deadcatlab.bakbak.exception.ForbiddenException;
import uk.deadcatlab.bakbak.exception.ResourceNotFoundException;
import uk.deadcatlab.bakbak.model.Attachment;
import uk.deadcatlab.bakbak.repository.AttachmentRepository;

/**
 * Attachment lifecycle: intent → direct upload → confirm on message send → download URL minting.
 */
@Service
public class AttachmentService {

	private final AttachmentRepository attachmentRepository;
	private final StorageService storageService;
	private final StorageProperties storageProperties;
	private final ConversationService conversationService;

	public AttachmentService(
		AttachmentRepository attachmentRepository,
		StorageService storageService,
		StorageProperties storageProperties,
		ConversationService conversationService
	) {
		this.attachmentRepository = attachmentRepository;
		this.storageService = storageService;
		this.storageProperties = storageProperties;
		this.conversationService = conversationService;
	}

	/**
	 * Creates a PENDING attachment row and returns a presigned PUT URL.
	 *
	 * <p>Participant authorization must be enforced by the caller before invoking this method.</p>
	 */
	@Transactional
	public AttachmentIntentResponse createIntent(
		Long conversationId,
		Long uploaderId,
		String mimeType,
		long sizeBytes,
		String fileName
	) {
		validateMimeType(mimeType);
		validateSize(sizeBytes);

		UUID attachmentId = UUID.randomUUID();
		String objectKey = buildObjectKey(conversationId, attachmentId, fileName);

		Attachment attachment = Attachment.builder()
			.id(attachmentId)
			.uploaderId(uploaderId)
			.conversationId(conversationId)
			.objectKey(objectKey)
			.mimeType(mimeType)
			.sizeBytes(sizeBytes)
			.status(AttachmentStatus.PENDING)
			.build();
		attachmentRepository.save(attachment);

		String uploadUrl = storageService.presignPut(objectKey, mimeType);
		return new AttachmentIntentResponse(attachmentId, uploadUrl);
	}

	/**
	 * Verifies the object exists in storage, then marks the attachment CONFIRMED and links it to
	 * the message.
	 */
	@Transactional
	public AttachmentSummary confirmForMessage(
		UUID attachmentId,
		UUID messageId,
		Long conversationId,
		Long uploaderId
	) {
		Attachment attachment = attachmentRepository.findByIdAndUploaderId(attachmentId, uploaderId)
			.orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

		if (!attachment.getConversationId().equals(conversationId)) {
			throw new ForbiddenException("Attachment does not belong to this conversation");
		}
		if (attachment.getStatus() != AttachmentStatus.PENDING) {
			throw new ForbiddenException("Attachment is not pending confirmation");
		}

		long actualSize = storageService.verifyObjectSize(attachment.getObjectKey());
		if (actualSize != attachment.getSizeBytes()) {
			throw new ForbiddenException("Uploaded object size does not match declared size");
		}

		attachment.setStatus(AttachmentStatus.CONFIRMED);
		attachment.setMessageId(messageId);
		attachmentRepository.save(attachment);

		return AttachmentSummary.from(attachment);
	}

	/**
	 * Mints a fresh presigned GET URL after verifying the caller is a conversation participant.
	 */
	@Transactional(readOnly = true)
	public AttachmentDownloadResponse getDownloadUrl(UUID attachmentId, Long requesterId) {
		Attachment attachment = attachmentRepository.findById(attachmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

		if (attachment.getStatus() != AttachmentStatus.CONFIRMED) {
			throw new ResourceNotFoundException("Attachment is not available for download");
		}

		conversationService.assertParticipant(attachment.getConversationId(), requesterId);

		String downloadUrl = storageService.presignGet(attachment.getObjectKey());
		return new AttachmentDownloadResponse(
			attachment.getId(),
			downloadUrl,
			attachment.getMimeType(),
			attachment.getSizeBytes()
		);
	}

	@Transactional(readOnly = true)
	public AttachmentSummary loadSummary(UUID attachmentId) {
		Attachment attachment = attachmentRepository.findById(attachmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
		return AttachmentSummary.from(attachment);
	}

	/** Marks stale PENDING uploads as ORPHANED and deletes their objects from storage. */
	@Scheduled(fixedRate = 3_600_000)
	@Transactional
	public void cleanupStalePending() {
		Instant cutoff = Instant.now().minus(storageProperties.getPendingTtlHours(), ChronoUnit.HOURS);
		List<Attachment> stale = attachmentRepository.findByStatusAndCreatedAtBefore(
			AttachmentStatus.PENDING,
			cutoff
		);

		for (Attachment attachment : stale) {
			attachment.setStatus(AttachmentStatus.ORPHANED);
			attachmentRepository.save(attachment);
			try {
				storageService.deleteObject(attachment.getObjectKey());
			} catch (RuntimeException ex) {
				// Best-effort cleanup; object may already be gone via lifecycle policy.
			}
		}
	}

	private void validateMimeType(String mimeType) {
		if (!storageProperties.getAllowedMimeTypes().contains(mimeType)) {
			throw new IllegalArgumentException("MIME type not allowed: " + mimeType);
		}
	}

	private void validateSize(long sizeBytes) {
		if (sizeBytes > storageProperties.getMaxSizeBytes()) {
			throw new IllegalArgumentException(
				"File exceeds maximum size of " + storageProperties.getMaxSizeBytes() + " bytes"
			);
		}
	}

	private static String buildObjectKey(Long conversationId, UUID attachmentId, String fileName) {
		String safeName = sanitizeFileName(fileName);
		return "attachments/" + conversationId + "/" + attachmentId + "/" + safeName;
	}

	private static String sanitizeFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return "file";
		}
		String base = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
		return base.isBlank() ? "file" : base.substring(0, Math.min(base.length(), 200));
	}
}
