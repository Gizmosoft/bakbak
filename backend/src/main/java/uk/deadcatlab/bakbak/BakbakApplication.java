package uk.deadcatlab.bakbak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entrypoint for the Bakbak backend.
 *
 * <p>We keep the bootstrap class minimal; environment-specific config lives in
 * {@code application-*.properties} and Flyway handles schema creation.</p>
 */
@SpringBootApplication
public class BakbakApplication {

	public static void main(String[] args) {
		// Useful when running from IDE / mvn spring-boot:run to confirm startup begins.
		System.out.println("Starting Bakbak Application");
		SpringApplication.run(BakbakApplication.class, args);
	}

}
