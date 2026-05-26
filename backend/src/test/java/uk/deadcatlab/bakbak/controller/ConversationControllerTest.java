package uk.deadcatlab.bakbak.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import uk.deadcatlab.bakbak.config.CorsConfig;
import uk.deadcatlab.bakbak.config.SecurityConfig;
import uk.deadcatlab.bakbak.dto.request.CreateConversationRequest;
import uk.deadcatlab.bakbak.dto.response.ConversationResponse;
import uk.deadcatlab.bakbak.dto.response.ConversationResponse.LastMessagePreview;
import uk.deadcatlab.bakbak.dto.response.UserPublicResponse;
import uk.deadcatlab.bakbak.exception.GlobalExceptionHandler;
import uk.deadcatlab.bakbak.exception.ResourceNotFoundException;
import uk.deadcatlab.bakbak.security.JwtAuthFilter;
import uk.deadcatlab.bakbak.security.JwtUtil;
import uk.deadcatlab.bakbak.security.UserDetailsServiceImpl;
import uk.deadcatlab.bakbak.service.ConversationService;
import uk.deadcatlab.bakbak.service.ConversationService.GetOrCreateResult;
import uk.deadcatlab.bakbak.service.UserService;

/**
 * Web-layer tests for {@link ConversationController}.
 *
 * <p>Covers idempotent create (201 vs 200), participant validation errors, JWT enforcement, and
 * the contact-window listing — all through the real security filter chain.</p>
 */
@WebMvcTest(ConversationController.class)
@Import({
	CorsConfig.class,
	SecurityConfig.class,
	JwtAuthFilter.class,
	JwtUtil.class,
	JacksonAutoConfiguration.class,
	GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
	"jwt.secret=" + WebLayerTestSupport.TEST_JWT_SECRET,
	"jwt.expiration-ms=3600000"
})
class ConversationControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JsonMapper objectMapper;
	@Autowired JwtUtil jwtUtil;

	@MockitoBean ConversationService conversationService;
	@MockitoBean UserService userService;
	@MockitoBean UserDetailsServiceImpl userDetailsService;

	@BeforeEach
	void setUpJwtPrincipal() {
		when(userDetailsService.loadUserById(eq(WebLayerTestSupport.TEST_USER_ID)))
			.thenReturn(WebLayerTestSupport.testPrincipal());
		when(userService.requireUserIdByUsername(WebLayerTestSupport.TEST_USERNAME))
			.thenReturn(WebLayerTestSupport.TEST_USER_ID);
	}

	private String aliceToken() {
		return jwtUtil.generateToken(WebLayerTestSupport.TEST_USER_ID, WebLayerTestSupport.TEST_USERNAME);
	}

	// ---------- POST /api/conversations ----------

	@Test
	void create_newConversation_returns201() throws Exception {
		ConversationResponse payload = new ConversationResponse(
			17L,
			new UserPublicResponse(42L, "bob", "Bob"),
			null, null);
		when(conversationService.getOrCreate(eq(WebLayerTestSupport.TEST_USER_ID), eq(42L)))
			.thenReturn(new GetOrCreateResult(true, payload));

		mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CreateConversationRequest(42L))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.conversationId").value(17))
			.andExpect(jsonPath("$.otherUser.id").value(42))
			.andExpect(jsonPath("$.otherUser.username").value("bob"))
			.andExpect(jsonPath("$.otherUser.displayName").value("Bob"))
			.andExpect(jsonPath("$.lastMessage").doesNotExist())
			.andExpect(jsonPath("$.lastMessageAt").doesNotExist());
	}

	@Test
	void create_existingConversation_returns200WithSameId() throws Exception {
		ConversationResponse payload = new ConversationResponse(
			17L,
			new UserPublicResponse(42L, "bob", "Bob"),
			new LastMessagePreview("see you at 5", 42L),
			Instant.parse("2026-04-19T14:32:00Z"));
		when(conversationService.getOrCreate(eq(WebLayerTestSupport.TEST_USER_ID), eq(42L)))
			.thenReturn(new GetOrCreateResult(false, payload));

		mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CreateConversationRequest(42L))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conversationId").value(17))
			.andExpect(jsonPath("$.lastMessage.content").value("see you at 5"))
			.andExpect(jsonPath("$.lastMessage.senderId").value(42))
			.andExpect(jsonPath("$.lastMessageAt").value("2026-04-19T14:32:00Z"));
	}

	@Test
	void create_selfConversation_returns400() throws Exception {
		when(conversationService.getOrCreate(eq(WebLayerTestSupport.TEST_USER_ID), any()))
			.thenThrow(new IllegalArgumentException("Cannot start a conversation with yourself"));

		mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new CreateConversationRequest(WebLayerTestSupport.TEST_USER_ID))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void create_unknownTargetUser_returns404() throws Exception {
		when(conversationService.getOrCreate(eq(WebLayerTestSupport.TEST_USER_ID), eq(99999L)))
			.thenThrow(new ResourceNotFoundException("User not found"));

		mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CreateConversationRequest(99999L))))
			.andExpect(status().isNotFound());
	}

	@Test
	void create_missingTargetUserId_returns400() throws Exception {
		mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(conversationService);
	}

	@Test
	void create_negativeTargetUserId_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(new CreateConversationRequest(-1L));

		mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verify(conversationService, never()).getOrCreate(any(), any());
	}

	@Test
	void create_zeroTargetUserId_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(new CreateConversationRequest(0L));

		mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verify(conversationService, never()).getOrCreate(any(), any());
	}

	@Test
	void create_withoutJwt_returns401() throws Exception {
		mockMvc.perform(post("/api/conversations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CreateConversationRequest(42L))))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(conversationService);
	}

	// ---------- GET /api/conversations ----------

	@Test
	void list_withConversations_returnsArray() throws Exception {
		when(conversationService.listForUser(WebLayerTestSupport.TEST_USER_ID))
			.thenReturn(List.of(
				new ConversationResponse(
					17L,
					new UserPublicResponse(42L, "bob", "Bob"),
					new LastMessagePreview("see you at 5", 42L),
					Instant.parse("2026-04-19T14:32:00Z")),
				new ConversationResponse(
					18L,
					new UserPublicResponse(43L, "carol", "Carol"),
					null, null)));

		mockMvc.perform(get("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].conversationId").value(17))
			.andExpect(jsonPath("$[0].otherUser.username").value("bob"))
			.andExpect(jsonPath("$[0].lastMessage.content").value("see you at 5"))
			.andExpect(jsonPath("$[1].conversationId").value(18))
			.andExpect(jsonPath("$[1].otherUser.username").value("carol"))
			.andExpect(jsonPath("$[1].lastMessage").doesNotExist());
	}

	@Test
	void list_emptyForUser_returnsEmptyArray() throws Exception {
		when(conversationService.listForUser(WebLayerTestSupport.TEST_USER_ID))
			.thenReturn(List.of());

		mockMvc.perform(get("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void list_withoutJwt_returns401() throws Exception {
		mockMvc.perform(get("/api/conversations"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(conversationService);
	}
}
