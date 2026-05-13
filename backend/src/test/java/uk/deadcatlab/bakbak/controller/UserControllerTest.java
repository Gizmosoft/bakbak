package uk.deadcatlab.bakbak.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.deadcatlab.bakbak.config.SecurityConfig;
import uk.deadcatlab.bakbak.dto.response.UserPublicResponse;
import uk.deadcatlab.bakbak.dto.response.UserResponse;
import uk.deadcatlab.bakbak.security.JwtAuthFilter;
import uk.deadcatlab.bakbak.security.JwtUtil;
import uk.deadcatlab.bakbak.security.UserDetailsServiceImpl;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Web-layer tests for {@link UserController}.
 *
 * <p>Exercises {@code GET /api/users/me} and {@code GET /api/users/search} through the real
 * security filter chain so JWT enforcement and the controller's limit clamping are covered.</p>
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class, JacksonAutoConfiguration.class})
@TestPropertySource(properties = {
	"jwt.secret=" + WebLayerTestSupport.TEST_JWT_SECRET,
	"jwt.expiration-ms=3600000"
})
class UserControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwtUtil jwtUtil;

	@MockitoBean UserService userService;
	@MockitoBean UserDetailsServiceImpl userDetailsService;

	@BeforeEach
	void setUpJwtPrincipal() {
		// JwtAuthFilter resolves the JWT subject (user id) via this method, then sets
		// authentication.getName() to the returned principal's username.
		when(userDetailsService.loadUserById(eq(WebLayerTestSupport.TEST_USER_ID)))
			.thenReturn(WebLayerTestSupport.testPrincipal());
		// requireCurrentUserId reverses that: username -> id.
		when(userService.requireUserIdByUsername(WebLayerTestSupport.TEST_USERNAME))
			.thenReturn(WebLayerTestSupport.TEST_USER_ID);
	}

	private String aliceToken() {
		return jwtUtil.generateToken(WebLayerTestSupport.TEST_USER_ID, WebLayerTestSupport.TEST_USERNAME);
	}

	// ---------- GET /api/users/me ----------

	@Test
	void me_withValidJwt_returnsCurrentUser() throws Exception {
		when(userService.getCurrentUser(WebLayerTestSupport.TEST_USER_ID))
			.thenReturn(new UserResponse(7L, "alice", "alice@example.com", "Alice"));

		mockMvc.perform(get("/api/users/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.username").value("alice"))
			.andExpect(jsonPath("$.email").value("alice@example.com"))
			.andExpect(jsonPath("$.displayName").value("Alice"));
	}

	@Test
	void me_withoutJwt_returns401() throws Exception {
		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(userService);
	}

	@Test
	void me_withMalformedJwt_returns401() throws Exception {
		mockMvc.perform(get("/api/users/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.jwt"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(userService);
	}

	@Test
	void me_withWrongSecretJwt_returns401() throws Exception {
		// A JWT signed with a different secret must be rejected by signature verification.
		JwtUtil otherIssuer = new JwtUtil(
			"different-secret-different-secret-different-secret-1234567890", 3600000L);
		String foreignToken = otherIssuer.generateToken(
			WebLayerTestSupport.TEST_USER_ID, WebLayerTestSupport.TEST_USERNAME);

		mockMvc.perform(get("/api/users/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(userService);
	}

	// ---------- GET /api/users/search ----------

	@Test
	void search_withQuery_returnsResults() throws Exception {
		when(userService.searchByUsername(eq("ali"), eq(WebLayerTestSupport.TEST_USER_ID), eq(20)))
			.thenReturn(List.of(
				new UserPublicResponse(11L, "alice99", "Alice"),
				new UserPublicResponse(12L, "alicia", "Alicia")));

		mockMvc.perform(get("/api/users/search")
				.param("q", "ali")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(11))
			.andExpect(jsonPath("$[0].username").value("alice99"))
			.andExpect(jsonPath("$[1].id").value(12))
			.andExpect(jsonPath("$[1].username").value("alicia"));
	}

	@Test
	void search_explicitLimit_isPassedThrough() throws Exception {
		when(userService.searchByUsername(eq("ali"), eq(WebLayerTestSupport.TEST_USER_ID), eq(5)))
			.thenReturn(List.of());

		mockMvc.perform(get("/api/users/search")
				.param("q", "ali").param("limit", "5")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken()))
			.andExpect(status().isOk());

		verify(userService, times(1))
			.searchByUsername(eq("ali"), eq(WebLayerTestSupport.TEST_USER_ID), eq(5));
	}

	@Test
	void search_limitAboveMax_isClampedTo100() throws Exception {
		when(userService.searchByUsername(eq("ali"), eq(WebLayerTestSupport.TEST_USER_ID), eq(100)))
			.thenReturn(List.of());

		mockMvc.perform(get("/api/users/search")
				.param("q", "ali").param("limit", "999")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken()))
			.andExpect(status().isOk());

		verify(userService).searchByUsername(eq("ali"), eq(WebLayerTestSupport.TEST_USER_ID), eq(100));
	}

	@Test
	void search_negativeLimit_isClampedTo1() throws Exception {
		when(userService.searchByUsername(eq("ali"), eq(WebLayerTestSupport.TEST_USER_ID), eq(1)))
			.thenReturn(List.of());

		mockMvc.perform(get("/api/users/search")
				.param("q", "ali").param("limit", "-5")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken()))
			.andExpect(status().isOk());

		verify(userService).searchByUsername(eq("ali"), eq(WebLayerTestSupport.TEST_USER_ID), eq(1));
	}

	@Test
	void search_missingQ_stillCallsService() throws Exception {
		// The controller hands the (possibly null) `q` to the service which is responsible for
		// returning an empty list — verify the wiring, not the service logic.
		when(userService.searchByUsername(eq(null), eq(WebLayerTestSupport.TEST_USER_ID), eq(20)))
			.thenReturn(List.of());

		mockMvc.perform(get("/api/users/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void search_withoutJwt_returns401() throws Exception {
		mockMvc.perform(get("/api/users/search").param("q", "ali"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(userService);
	}
}
