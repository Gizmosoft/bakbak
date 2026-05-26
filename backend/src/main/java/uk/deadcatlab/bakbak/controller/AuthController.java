package uk.deadcatlab.bakbak.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.deadcatlab.bakbak.dto.request.LoginRequest;
import uk.deadcatlab.bakbak.dto.request.RegisterRequest;
import uk.deadcatlab.bakbak.dto.response.AuthResponse;
import uk.deadcatlab.bakbak.service.AuthService;

/**
 * Public authentication endpoints.
 *
 * <p>These routes are intentionally left unauthenticated in {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		AuthResponse response = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}
}

