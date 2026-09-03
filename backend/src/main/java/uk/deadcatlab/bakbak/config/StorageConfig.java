package uk.deadcatlab.bakbak.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

	@Bean
	S3Client s3Client(StorageProperties properties) {
		validateStorageProperties(properties);
		return S3Client.builder()
			.endpointOverride(requireEndpointUri(properties.getEndpoint()))
			.region(Region.of(properties.getRegion()))
			.credentialsProvider(credentials(properties))
			// MinIO / S3-compatible: do not also set checksumValidationEnabled on S3Configuration.
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.serviceConfiguration(s3ServiceConfig())
			.build();
	}

	@Bean
	S3Presigner s3Presigner(StorageProperties properties) {
		validateStorageProperties(properties);
		return S3Presigner.builder()
			.endpointOverride(requireEndpointUri(properties.getEndpoint()))
			.region(Region.of(properties.getRegion()))
			.credentialsProvider(credentials(properties))
			.serviceConfiguration(s3ServiceConfig())
			.build();
	}

	private static S3Configuration s3ServiceConfig() {
		return S3Configuration.builder()
			.pathStyleAccessEnabled(true)
			.chunkedEncodingEnabled(false)
			.build();
	}

	private static void validateStorageProperties(StorageProperties properties) {
		requireResolved("S3_ENDPOINT", properties.getEndpoint());
		requireResolved("S3_ACCESS_KEY", properties.getAccessKey());
		requireResolved("S3_SECRET_KEY", properties.getSecretKey());
	}

	private static void requireResolved(String envVar, String value) {
		if (value == null || value.isBlank() || value.contains("${")) {
			throw new IllegalStateException(
				envVar + " is not set. Add it to backend/.env (see .env.example) and re-run with: "
					+ "set -a && source .env && set +a"
			);
		}
	}

	private static URI requireEndpointUri(String endpoint) {
		requireResolved("S3_ENDPOINT", endpoint);
		return URI.create(endpoint);
	}

	private static StaticCredentialsProvider credentials(StorageProperties properties) {
		return StaticCredentialsProvider.create(
			AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
		);
	}
}
