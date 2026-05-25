package uk.deadcatlab.bakbak.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import uk.deadcatlab.bakbak.websocket.WebSocketAuthInterceptor;
import uk.deadcatlab.bakbak.websocket.WebSocketAuthorizationInterceptor;

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
 * <p>Inbound channel interceptors (in order): {@link WebSocketAuthInterceptor} (JWT on
 * {@code CONNECT}), then {@link WebSocketAuthorizationInterceptor} (participant checks on
 * {@code SUBSCRIBE} / {@code SEND}).</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final WebSocketAuthInterceptor webSocketAuthInterceptor;
	private final WebSocketAuthorizationInterceptor webSocketAuthorizationInterceptor;

	public WebSocketConfig(
		WebSocketAuthInterceptor webSocketAuthInterceptor,
		WebSocketAuthorizationInterceptor webSocketAuthorizationInterceptor
	) {
		this.webSocketAuthInterceptor = webSocketAuthInterceptor;
		this.webSocketAuthorizationInterceptor = webSocketAuthorizationInterceptor;
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(webSocketAuthInterceptor, webSocketAuthorizationInterceptor);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// This is the endpoint that the client will connect to.
		// This speaks SockJS, which is a fallback for browsers that don't support WebSocket.
		registry.addEndpoint("/ws")
			// Broad pattern for local dev
			.setAllowedOriginPatterns("*")
			.withSockJS();
		
		// This is the endpoint that the client will connect to.
		// This speaks plain WebSocket, which is a fallback for browsers that don't support SockJS.
		registry.addEndpoint("/ws-native")
			// Broad pattern for local dev
    		.setAllowedOriginPatterns("*");
	}
}
