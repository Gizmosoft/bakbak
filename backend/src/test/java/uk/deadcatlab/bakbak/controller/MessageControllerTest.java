package uk.deadcatlab.bakbak.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
import uk.deadcatlab.bakbak.config.CorsConfig;
import uk.deadcatlab.bakbak.config.SecurityConfig;
import uk.deadcatlab.bakbak.exception.ForbiddenException;
import uk.deadcatlab.bakbak.exception.GlobalExceptionHandler;
import uk.deadcatlab.bakbak.exception.ResourceNotFoundException;
import uk.deadcatlab.bakbak.security.JwtAuthFilter;
import uk.deadcatlab.bakbak.security.JwtUtil;
import uk.deadcatlab.bakbak.security.UserDetailsServiceImpl;
import uk.deadcatlab.bakbak.service.ConversationService;
import uk.deadcatlab.bakbak.service.MessageService;
import uk.deadcatlab.bakbak.service.UserService;

@WebMvcTest(MessageController.class)
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
class MessageControllerTest {

	@Autowired MockMvc mockMvc;

	@MockitoBean MessageService messageService;
	@MockitoBean ConversationService conversationService;
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
	void listMessages_participant_returns200() throws Exception {
		when(messageService.getHistory(eq(3L), eq(null), eq(50))).thenReturn(List.of());

		mockMvc.perform(get("/api/conversations/3/messages").header(HttpHeaders.AUTHORIZATION, authHeader))
			.andExpect(status().isOk());

		verify(conversationService).requireConversationExists(3L);
		verify(conversationService).assertParticipant(3L, WebLayerTestSupport.TEST_USER_ID);
	}

	@Test
	void listMessages_notParticipant_returns403() throws Exception {
		doThrow(new ForbiddenException("Not a participant of this conversation"))
			.when(conversationService)
			.assertParticipant(eq(3L), eq(WebLayerTestSupport.TEST_USER_ID));

		mockMvc.perform(get("/api/conversations/3/messages").header(HttpHeaders.AUTHORIZATION, authHeader))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.message").value("Not a participant of this conversation"))
			.andExpect(jsonPath("$.path").value("/api/conversations/3/messages"));

		verify(messageService, org.mockito.Mockito.never()).getHistory(any(), any(), any(Integer.class));
	}

	@Test
	void listMessages_unknownConversation_returns404() throws Exception {
		doThrow(new ResourceNotFoundException("Conversation not found"))
			.when(conversationService)
			.requireConversationExists(3L);

		mockMvc.perform(get("/api/conversations/3/messages").header(HttpHeaders.AUTHORIZATION, authHeader))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Conversation not found"));
	}

	@Test
	void listMessages_withoutJwt_returns401() throws Exception {
		mockMvc.perform(get("/api/conversations/3/messages"))
			.andExpect(status().isUnauthorized());
	}
}
