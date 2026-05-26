package uk.deadcatlab.bakbak.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Central CORS policy for REST endpoints ({@code DESIGN.md} Step 34).
 *
 * <p>Wired into {@link SecurityConfig} via {@code http.cors(...)}. WebSocket STOMP origins are
 * configured separately in {@link WebSocketConfig}.</p>
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
		configuration.setAllowedMethods(corsProperties.getAllowedMethods());
		configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(corsProperties.getMaxAge());

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}
}
