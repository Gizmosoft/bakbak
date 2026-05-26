package uk.deadcatlab.bakbak.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.deadcatlab.bakbak.security.UserDetailsServiceImpl;
import uk.deadcatlab.bakbak.controller.AuthController;
import uk.deadcatlab.bakbak.exception.GlobalExceptionHandler;
import uk.deadcatlab.bakbak.security.JwtAuthFilter;
import uk.deadcatlab.bakbak.security.JwtUtil;
import uk.deadcatlab.bakbak.service.AuthService;

/**
 * Verifies CORS preflight handling for future Expo / browser clients.
 */
@WebMvcTest(AuthController.class)
@Import({
	CorsConfig.class,
	SecurityConfig.class,
	JwtAuthFilter.class,
	JwtUtil.class,
	JacksonAutoConfiguration.class,
	GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
	"jwt.secret=test-secret-test-secret-test-secret-test-secret-test-secret-1234",
	"jwt.expiration-ms=3600000",
	"app.cors.allowed-origin-patterns=http://localhost:*,http://127.0.0.1:*"
})
class CorsConfigTest {

	@Autowired MockMvc mockMvc;

	@MockitoBean AuthService authService;
	@MockitoBean UserDetailsServiceImpl userDetailsService;

	@Test
	void preflight_allowedOrigin_returnsCorsHeaders() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header("Origin", "http://localhost:8081")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "Authorization,Content-Type"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
			.andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS"));
	}

	@Test
	void preflight_disallowedOrigin_omitsAllowOrigin() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header("Origin", "http://evil.example")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}
}
