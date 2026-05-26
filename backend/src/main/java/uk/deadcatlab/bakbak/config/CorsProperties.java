package uk.deadcatlab.bakbak.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS settings for HTTP API access from browsers and Expo web ({@code DESIGN.md} Step 34).
 *
 * <p>Native React Native fetch calls typically do not send an {@code Origin} header; CORS mainly
 * affects Expo web, local dev tools, and future browser clients.</p>
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

	/**
	 * Ant-style origin patterns (e.g. {@code http://localhost:*} for Metro / Expo web).
	 */
	private List<String> allowedOriginPatterns = List.of(
		"http://localhost:*",
		"http://127.0.0.1:*"
	);

	private List<String> allowedMethods = List.of(
		"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
	);

	private List<String> allowedHeaders = List.of(
		"Authorization",
		"Content-Type",
		"Accept"
	);

	/** Preflight cache duration in seconds. */
	private long maxAge = 3600L;

	public List<String> getAllowedOriginPatterns() {
		return allowedOriginPatterns;
	}

	public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
		this.allowedOriginPatterns = allowedOriginPatterns;
	}

	public List<String> getAllowedMethods() {
		return allowedMethods;
	}

	public void setAllowedMethods(List<String> allowedMethods) {
		this.allowedMethods = allowedMethods;
	}

	public List<String> getAllowedHeaders() {
		return allowedHeaders;
	}

	public void setAllowedHeaders(List<String> allowedHeaders) {
		this.allowedHeaders = allowedHeaders;
	}

	public long getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(long maxAge) {
		this.maxAge = maxAge;
	}
}
