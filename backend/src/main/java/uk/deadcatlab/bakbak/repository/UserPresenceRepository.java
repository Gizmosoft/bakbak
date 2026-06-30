package uk.deadcatlab.bakbak.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.deadcatlab.bakbak.model.UserPresence;

/**
 * Persistence operations for {@link UserPresence} rows.
 */
public interface UserPresenceRepository extends JpaRepository<UserPresence, Long> {

	Optional<UserPresence> findByUserId(Long userId);

	List<UserPresence> findAllByUserIdIn(Collection<Long> userIds);
}
