package uk.deadcatlab.bakbak.security;

import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import uk.deadcatlab.bakbak.repository.UserRepository;

/**
 * Bridges our {@code users} table to Spring Security's {@link UserDetailsService}.
 *
 * <p>JWT auth will typically resolve a user by ID (from {@code sub}) while the default contract is
 * username-based. We support both to keep the rest of the security pipeline simple.</p>
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	private final UserRepository userRepository;

	public UserDetailsServiceImpl(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		var user = userRepository.findByUsername(username)
			.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

		return org.springframework.security.core.userdetails.User.builder()
			.username(user.getUsername())
			.password(user.getPasswordHash())
			.authorities(List.of())
			.build();
	}

	public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
		// Used by the JWT filter where subject is the numeric user id.
		var user = userRepository.findById(userId)
			.orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

		return org.springframework.security.core.userdetails.User.builder()
			.username(user.getUsername())
			.password(user.getPasswordHash())
			.authorities(List.of())
			.build();
	}
}

