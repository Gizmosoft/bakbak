package uk.deadcatlab.bakbak.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.deadcatlab.bakbak.model.SignedPreKey;

public interface SignedPreKeyRepository extends JpaRepository<SignedPreKey, SignedPreKey.SignedPreKeyId> {

	@Query("""
		SELECT s FROM SignedPreKey s
		WHERE s.userId = :userId
		ORDER BY s.createdAt DESC
		""")
	List<SignedPreKey> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

	default Optional<SignedPreKey> findLatestByUserId(Long userId) {
		List<SignedPreKey> keys = findAllByUserIdOrderByCreatedAtDesc(userId);
		return keys.isEmpty() ? Optional.empty() : Optional.of(keys.getFirst());
	}

	Optional<SignedPreKey> findByUserIdAndKeyId(Long userId, Integer keyId);
}
