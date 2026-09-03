package uk.deadcatlab.bakbak.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.deadcatlab.bakbak.dto.AttachmentStatus;
import uk.deadcatlab.bakbak.model.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

	Optional<Attachment> findByIdAndUploaderId(UUID id, Long uploaderId);

	List<Attachment> findByStatusAndCreatedAtBefore(AttachmentStatus status, Instant cutoff);
}
