package uk.deadcatlab.bakbak.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import uk.deadcatlab.bakbak.websocket.WebSocketAuthInterceptor;

/**
 * STOMP-over-WebSocket messaging infrastructure.
 *
 * <p>Matches the MVP contract in {@code DESIGN.md}</p>
 * <ul>
 *   <li>Endpoint {@code /ws} (SockJS enabled as an optional fallback for clients that need it).</li>
 *   <li>Broker prefixes {@code /topic} and {@code /queue} for broadcasts and user-targeted queues.</li>
 *   <li>Application prefix {@code /app} for inbound {@code @MessageMapping} destinations such as
 *       {@code /app/chat/{conversationId}}.</li>
 *   <li>User destination prefix {@code /user} so clients can subscribe to {@code /user/queue/errors}.</li>
 * </ul>
 *
 * <p>JWT validation on the STOMP {@code CONNECT} frame is handled by
 * {@link WebSocketAuthInterceptor}; participant checks on subscribe/send follow in Step 31.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final WebSocketAuthInterceptor webSocketAuthInterceptor;

	public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
		this.webSocketAuthInterceptor = webSocketAuthInterceptor;
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(webSocketAuthInterceptor);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
			// Broad pattern for local dev
			.setAllowedOriginPatterns("*")
			.withSockJS();
	}
}
