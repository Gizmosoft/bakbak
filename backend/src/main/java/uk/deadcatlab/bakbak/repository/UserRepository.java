package uk.deadcatlab.bakbak.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.deadcatlab.bakbak.model.User;

/**
 * Persistence operations for {@link User}.
 *
 * <p>Spring Data derives implementations from method names; custom queries should stay small and focused
 * (business logic belongs in services).</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {
	/** Used by login and by {@link uk.deadcatlab.bakbak.security.UserDetailsServiceImpl}. */
	Optional<User> findByUsername(String username);

	/** Used to enforce unique emails at registration time. */
	Optional<User> findByEmail(String email);

	/**
	 * Case-insensitive username prefix search, excluding a user id (typically the caller).
	 *
	 * <p>Pagination is applied via {@link Pageable} so we don't rely on provider-specific {@code LIMIT} JPQL.</p>
	 */
	@Query("""
			select u from User u
			where lower(u.username) like lower(concat(:prefix, '%'))
			  and (:excludeUserId is null or u.id <> :excludeUserId)
			order by u.username asc
			""")
	List<User> searchByUsernamePrefix(@Param("prefix") String prefix, @Param("excludeUserId") Long excludeUserId, Pageable pageable);
}

