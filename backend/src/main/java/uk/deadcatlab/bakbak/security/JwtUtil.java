package uk.deadcatlab.bakbak.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Minimal JWT helper used by the HTTP filter and WebSocket interceptor.
 *
 * <p>Tokens are HS256-signed and contain {@code sub=userId} plus a {@code username} claim, matching
 * the design doc. Expiration is controlled via {@code jwt.expiration-ms}.</p>
 */
@Component
public class JwtUtil {
	private static final String USERNAME_CLAIM = "username";

	private final SecretKey signingKey;
	private final long expirationMs;

	public JwtUtil(
		@Value("${jwt.secret}") String secret,
		@Value("${jwt.expiration-ms}") long expirationMs
	) {
		this.signingKey = buildSigningKey(secret);
		this.expirationMs = expirationMs;
	}

	public String generateToken(Long userId, String username) {
		Instant now = Instant.now();
		Instant exp = now.plusMillis(expirationMs);

		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim(USERNAME_CLAIM, username)
			.issuedAt(Date.from(now))
			.expiration(Date.from(exp))
			.signWith(signingKey, Jwts.SIG.HS256)
			.compact();
	}

	public Long getUserId(String token) {
		String sub = parseClaims(token).getSubject();
		return Long.valueOf(sub);
	}

	public String getUsername(String token) {
		Object value = parseClaims(token).get(USERNAME_CLAIM);
		return value == null ? null : String.valueOf(value);
	}

	public boolean isTokenValid(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

	public Claims parseClaims(String token) {
		// parseSignedClaims will validate signature and expiration (and throw on invalid tokens).
		return Jwts.parser()
			.verifyWith(signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	private static SecretKey buildSigningKey(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("jwt.secret must be set");
		}

		// Allow either a raw string secret or a base64-encoded secret (handy across envs).
		byte[] keyBytes;
		try {
			keyBytes = Decoders.BASE64.decode(secret);
		} catch (RuntimeException ex) {
			// JJWT commonly throws io.jsonwebtoken.io.DecodingException for invalid base64.
			keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		}

		if (keyBytes.length < 32) {
			throw new IllegalArgumentException("jwt.secret must be at least 256 bits (32 bytes)");
		}

		return Keys.hmacShaKeyFor(keyBytes);
	}
}

