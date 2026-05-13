package uk.deadcatlab.bakbak.controller;

import java.util.List;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Constants and tiny helpers shared by controller-slice tests.
 *
 * <p>Each {@code @WebMvcTest} class wires up the same JWT secret and a fixed test principal so
 * tests can mint tokens via {@link uk.deadcatlab.bakbak.security.JwtUtil} and have the
 * (mocked) {@code UserDetailsServiceImpl} resolve them.</p>
 */
final class WebLayerTestSupport {

	/**
	 * 64-char ASCII secret — well over the 32-byte minimum HS256 requires.
	 *
	 * <p>Used in {@code @TestPropertySource} as part of a compile-time constant expression, hence
	 * package-private {@code static final String}.</p>
	 */
	static final String TEST_JWT_SECRET =
		"test-secret-test-secret-test-secret-test-secret-test-secret-1234";

	static final long TEST_USER_ID = 7L;
	static final String TEST_USERNAME = "alice";

	/** Default principal returned by the mocked {@code UserDetailsServiceImpl} for {@link #TEST_USER_ID}. */
	static UserDetails testPrincipal() {
		return User.builder()
			.username(TEST_USERNAME)
			.password("ignored-by-tests")
			.authorities(List.of())
			.build();
	}

	private WebLayerTestSupport() {}
}
