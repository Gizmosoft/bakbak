package uk.deadcatlab.bakbak.service;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.deadcatlab.bakbak.dto.response.UserPublicResponse;
import uk.deadcatlab.bakbak.dto.response.UserResponse;
import uk.deadcatlab.bakbak.exception.ResourceNotFoundException;
import uk.deadcatlab.bakbak.model.User;
import uk.deadcatlab.bakbak.repository.UserRepository;

/**
 * User lookup helpers used by profile/search endpoints.
 *
 * <p>Keep entity mapping to DTOs here (or in a dedicated mapper) so controllers stay thin.</p>
 */
@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public User findById(Long id) {
		return userRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	@Transactional(readOnly = true)
	public User findByEmail(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	@Transactional(readOnly = true)
	public List<UserPublicResponse> searchByUsername(String prefix, Long excludeUserId, int limit) {
		if (limit <= 0) {
			return List.of();
		}

		String normalizedPrefix = prefix == null ? "" : prefix.trim();
		if (normalizedPrefix.isEmpty()) {
			return List.of();
		}

		List<User> matches = userRepository.searchByUsernamePrefix(
			normalizedPrefix,
			excludeUserId,
			PageRequest.of(0, limit)
		);

		return matches.stream().map(UserService::toPublicResponse).toList();
	}

	@Transactional(readOnly = true)
	public UserResponse getCurrentUser(Long userId) {
		return toUserResponse(findById(userId));
	}

	/**
	 * Resolves the numeric user id for the principal name stored in the security context (JWT flow uses username).
	 */
	@Transactional(readOnly = true)
	public long requireUserIdByUsername(String username) {
		return userRepository.findByUsername(username)
			.map(User::getId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	private static UserResponse toUserResponse(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
	}

	private static UserPublicResponse toPublicResponse(User user) {
		return new UserPublicResponse(user.getId(), user.getUsername(), user.getDisplayName());
	}
}
