package uk.deadcatlab.bakbak.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import uk.deadcatlab.bakbak.security.JwtUtil;
import uk.deadcatlab.bakbak.security.UserDetailsServiceImpl;

/**
 * Validates JWT on the inbound STOMP {@code CONNECT} frame and attaches an authenticated
 * {@link org.springframework.security.core.Authentication} to the WebSocket session.
 *
 * <p>Matches {@code DESIGN.md} §8.1 / §9.4: the client sends
 * {@code Authorization: Bearer <jwt>} on {@code CONNECT} (not on the HTTP handshake). Later
 * {@code SUBSCRIBE} / {@code SEND} authorization is enforced in a separate interceptor (Step 31).</p>
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtUtil jwtUtil;
	private final UserDetailsServiceImpl userDetailsService;

	public WebSocketAuthInterceptor(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
		this.jwtUtil = jwtUtil;
		this.userDetailsService = userDetailsService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
			return message;
		}

		String token = extractBearerToken(accessor);
		if (token == null || !jwtUtil.isTokenValid(token)) {
			throw new BadCredentialsException("Invalid or missing JWT on STOMP CONNECT");
		}

		Long userId = jwtUtil.getUserId(token);
		UserDetails userDetails = userDetailsService.loadUserById(userId);

		var authentication = new UsernamePasswordAuthenticationToken(
			userDetails,
			null,
			userDetails.getAuthorities()
		);
		accessor.setUser(authentication);
		return message;
	}

	private static String extractBearerToken(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}
}
