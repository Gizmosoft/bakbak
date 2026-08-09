package uk.deadcatlab.bakbak.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import uk.deadcatlab.bakbak.config.CorsConfig;
import uk.deadcatlab.bakbak.config.SecurityConfig;
import uk.deadcatlab.bakbak.exception.GlobalExceptionHandler;
import uk.deadcatlab.bakbak.model.OutboxMessage;
import uk.deadcatlab.bakbak.security.JwtAuthFilter;
import uk.deadcatlab.bakbak.security.JwtUtil;
import uk.deadcatlab.bakbak.security.UserDetailsServiceImpl;
import uk.deadcatlab.bakbak.service.OutboxService;
import uk.deadcatlab.bakbak.service.UserService;

@WebMvcTest(InboxController.class)
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
class InboxControllerTest {

	@Autowired MockMvc mockMvc;

	@MockitoBean OutboxService outboxService;
	@MockitoBean UserService userService;
	@MockitoBean UserDetailsServiceImpl userDetailsService;

	@Autowired JwtUtil jwtUtil;

	private String authHeader;

	@BeforeEach
	void setUp() {
		when(userService.requireUserIdByUsername(WebLayerTestSupport.TEST_USERNAME))
			.thenReturn(WebLayerTestSupport.TEST_USER_ID);
		when(userDetailsService.loadUserById(WebLayerTestSupport.TEST_USER_ID))
			.thenReturn(WebLayerTestSupport.testPrincipal());
		authHeader = "Bearer " + jwtUtil.generateToken(
			WebLayerTestSupport.TEST_USER_ID,
			WebLayerTestSupport.TEST_USERNAME
		);
	}

	@Test
	void listPending_returnsEnvelopeShape() throws Exception {
		UUID messageId = UUID.randomUUID();
		OutboxMessage row = OutboxMessage.builder()
			.messageId(messageId)
			.conversationId(5L)
			.senderId(9L)
			.recipientId(WebLayerTestSupport.TEST_USER_ID)
			.content("offline hello")
			.createdAt(Instant.parse("2026-04-19T14:32:00Z"))
			.build();
		when(outboxService.listPendingForRecipient(WebLayerTestSupport.TEST_USER_ID)).thenReturn(List.of(row));

		mockMvc.perform(get("/api/inbox/pending").header(HttpHeaders.AUTHORIZATION, authHeader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(messageId.toString()))
			.andExpect(jsonPath("$[0].conversationId").value(5))
			.andExpect(jsonPath("$[0].senderId").value(9))
			.andExpect(jsonPath("$[0].content").value("offline hello"))
			.andExpect(jsonPath("$[0].type").value("CHAT"))
			.andExpect(jsonPath("$[0].encryption").value("NONE"));

		verify(outboxService).listPendingForRecipient(WebLayerTestSupport.TEST_USER_ID);
	}

	@Test
	void listPending_withoutJwt_returns401() throws Exception {
		mockMvc.perform(get("/api/inbox/pending"))
			.andExpect(status().isUnauthorized());
	}
}
