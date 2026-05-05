package uk.deadcatlab.bakbak;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that verifies the Spring context can boot.
 *
 * <p>We run under the {@code test} profile so tests never point at the dev database.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class BakbakApplicationTests {

	@Test
	void contextLoads() {
	}

}
