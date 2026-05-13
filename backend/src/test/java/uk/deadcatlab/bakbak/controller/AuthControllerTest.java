package uk.deadcatlab.bakbak.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import uk.deadcatlab.bakbak.config.SecurityConfig;
import uk.deadcatlab.bakbak.dto.request.LoginRequest;
import uk.deadcatlab.bakbak.dto.request.RegisterRequest;
import uk.deadcatlab.bakbak.dto.response.AuthResponse;
import uk.deadcatlab.bakbak.dto.response.UserResponse;
import uk.deadcatlab.bakbak.security.JwtAuthFilter;
import uk.deadcatlab.bakbak.security.JwtUtil;
import uk.deadcatlab.bakbak.security.UserDetailsServiceImpl;
import uk.deadcatlab.bakbak.service.AuthService;

/**
 * Web-layer tests for {@link AuthController}.
 *
 * <p>Loads the real {@link SecurityConfig} + JWT filter so 4xx semantics (validation,
 * conflict, bad credentials) are exercised end-to-end through the HTTP / security stack.</p>
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class, JacksonAutoConfiguration.class})
@TestPropertySource(properties = {
	"jwt.secret=" + WebLayerTestSupport.TEST_JWT_SECRET,
	"jwt.expiration-ms=3600000"
})
class AuthControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JsonMapper objectMapper;

	@MockitoBean AuthService authService;
	// JwtAuthFilter depends on it; we don't drag UserRepository/JPA into a web slice.
	@MockitoBean UserDetailsServiceImpl userDetailsService;

	// ---------- POST /api/auth/register ----------

	@Test
	void register_validRequest_returns201AndBody() throws Exception {
		RegisterRequest request = new RegisterRequest(
			"alice", "alice@example.com", "Str0ngPass!", "Alice", LocalDate.of(1998, 5, 12));
		AuthResponse response = new AuthResponse(
			"jwt.token", new UserResponse(1L, "alice", "alice@example.com", "Alice"));
		when(authService.register(any(RegisterRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.token").value("jwt.token"))
			.andExpect(jsonPath("$.user.id").value(1))
			.andExpect(jsonPath("$.user.username").value("alice"))
			.andExpect(jsonPath("$.user.email").value("alice@example.com"))
			.andExpect(jsonPath("$.user.displayName").value("Alice"));
	}

	@Test
	void register_serviceReportsConflict_returns409() throws Exception {
		when(authService.register(any(RegisterRequest.class)))
			.thenThrow(new IllegalStateException("Email is already registered"));
		String body = objectMapper.writeValueAsString(new RegisterRequest(
			"alice", "alice@example.com", "Str0ngPass!", "Alice", LocalDate.of(1998, 5, 12)));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isConflict());
	}

	@Test
	void register_blankUsername_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(new RegisterRequest(
			"", "alice@example.com", "Str0ngPass!", "Alice", LocalDate.of(1998, 5, 12)));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void register_usernameTooShort_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(new RegisterRequest(
			"al", "alice@example.com", "Str0ngPass!", "Alice", LocalDate.of(1998, 5, 12)));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void register_usernameWithIllegalChars_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(new RegisterRequest(
			"alice space!", "alice@example.com", "Str0ngPass!", "Alice", LocalDate.of(1998, 5, 12)));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void register_invalidEmail_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(new RegisterRequest(
			"alice", "not-an-email", "Str0ngPass!", "Alice", LocalDate.of(1998, 5, 12)));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void register_passwordTooShort_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(new RegisterRequest(
			"alice", "alice@example.com", "short", "Alice", LocalDate.of(1998, 5, 12)));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void register_dobInFuture_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(new RegisterRequest(
			"alice", "alice@example.com", "Str0ngPass!", "Alice", LocalDate.now().plusDays(1)));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void register_missingDob_returns400() throws Exception {
		// Build a payload with no dateOfBirth so @NotNull triggers.
		String body = objectMapper.writeValueAsString(Map.of(
			"username", "alice",
			"email", "alice@example.com",
			"password", "Str0ngPass!",
			"displayName", "Alice"
		));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void register_malformedJson_returns400() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not-json"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	// ---------- POST /api/auth/login ----------

	@Test
	void login_validCredentials_returns200() throws Exception {
		LoginRequest request = new LoginRequest("alice@example.com", "Str0ngPass!");
		AuthResponse response = new AuthResponse(
			"jwt.token", new UserResponse(1L, "alice", "alice@example.com", "Alice"));
		when(authService.login(any(LoginRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value("jwt.token"))
			.andExpect(jsonPath("$.user.id").value(1))
			.andExpect(jsonPath("$.user.email").value("alice@example.com"));
	}

	@Test
	void login_badCredentials_returns401() throws Exception {
		when(authService.login(any(LoginRequest.class)))
			.thenThrow(new BadCredentialsException("Invalid credentials"));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new LoginRequest("alice@example.com", "Str0ngPass!"))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void login_invalidEmail_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(
			new LoginRequest("not-an-email", "Str0ngPass!"));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void login_blankPassword_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(
			new LoginRequest("alice@example.com", ""));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	void login_isPublic_noAuthHeaderRequired() throws Exception {
		when(authService.login(any(LoginRequest.class))).thenReturn(new AuthResponse(
			"jwt.token", new UserResponse(1L, "alice", "alice@example.com", "Alice")));

		// No Authorization header at all — security config must permitAll() for /api/auth/**.
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new LoginRequest("alice@example.com", "Str0ngPass!"))))
			.andExpect(status().isOk());
	}
}
