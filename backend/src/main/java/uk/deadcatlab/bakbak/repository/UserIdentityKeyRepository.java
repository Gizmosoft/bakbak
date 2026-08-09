package uk.deadcatlab.bakbak.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.deadcatlab.bakbak.model.UserIdentityKey;

public interface UserIdentityKeyRepository extends JpaRepository<UserIdentityKey, Long> {

	Optional<UserIdentityKey> findByUserId(Long userId);
}
