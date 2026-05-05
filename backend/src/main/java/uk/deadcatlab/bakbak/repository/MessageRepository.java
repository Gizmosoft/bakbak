package uk.deadcatlab.bakbak.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.deadcatlab.bakbak.model.Message;

/**
 * Persistence operations for {@link Message}.
 *
 * <p>Message history pagination queries will be added here in later phases.</p>
 */
public interface MessageRepository extends JpaRepository<Message, Long> {
}

