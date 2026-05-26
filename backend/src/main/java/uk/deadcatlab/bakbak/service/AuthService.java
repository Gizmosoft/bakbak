package uk.deadcatlab.bakbak.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.deadcatlab.bakbak.dto.request.LoginRequest;
import uk.deadcatlab.bakbak.dto.request.RegisterRequest;
import uk.deadcatlab.bakbak.dto.response.AuthResponse;
import uk.deadcatlab.bakbak.dto.response.UserResponse;
import uk.deadcatlab.bakbak.exception.ConflictException;
import uk.deadcatlab.bakbak.model.User;
import uk.deadcatlab.bakbak.repository.UserRepository;
import uk.deadcatlab.bakbak.security.JwtUtil;

/**
 * Authentication use-cases: register and login.
 *
 * <p>This is the only place that should deal with password hashing/verification and JWT issuance.</p>
 */
@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwtUtil;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtUtil = jwtUtil;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		// App-level uniqueness checks give cleaner errors than relying solely on DB constraints.
		userRepository.findByUsername(request.username()).ifPresent(u -> {
			throw new ConflictException("Username is already taken");
		});
		userRepository.findByEmail(request.email()).ifPresent(u -> {
			throw new ConflictException("Email is already registered");
		});

		User user = new User();
		user.setUsername(request.username());
		user.setEmail(request.email());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setDisplayName(normalizeOptional(request.displayName()));
		user.setDateOfBirth(request.dateOfBirth());

		User saved = userRepository.save(user);

		String token = jwtUtil.generateToken(saved.getId(), saved.getUsername());
		return new AuthResponse(token, toUserResponse(saved));
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
			.orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BadCredentialsException("Invalid credentials");
		}

		String token = jwtUtil.generateToken(user.getId(), user.getUsername());
		return new AuthResponse(token, toUserResponse(user));
	}

	private static UserResponse toUserResponse(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
	}

	private static String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}

