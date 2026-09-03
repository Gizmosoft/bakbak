package uk.deadcatlab.bakbak.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import uk.deadcatlab.bakbak.config.StorageProperties;
import uk.deadcatlab.bakbak.exception.ForbiddenException;
import uk.deadcatlab.bakbak.exception.ResourceNotFoundException;

/**
 * Thin wrapper around S3 presign, verify, and delete operations.
 *
 * <p>File bytes never pass through the application for chat uploads — clients upload/download
 * directly against object storage using presigned URLs. Confirm uses {@code ListObjectsV2}
 * (not HEAD/GET) so MinIO / reverse proxies never see Range or flexible-checksum headers that
 * break SigV4.</p>
 */
@Service
public class StorageService {

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final StorageProperties properties;

	public StorageService(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.properties = properties;
	}

	public String presignPut(String objectKey, String mimeType) {
		PutObjectRequest putRequest = PutObjectRequest.builder()
			.bucket(properties.getBucket())
			.key(objectKey)
			.contentType(mimeType)
			.build();

		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(properties.getPresignUploadTtlMinutes()))
			.putObjectRequest(putRequest)
			.build();

		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
		return presigned.url().toString();
	}

	public String presignGet(String objectKey) {
		GetObjectRequest getRequest = GetObjectRequest.builder()
			.bucket(properties.getBucket())
			.key(objectKey)
			.build();

		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(properties.getPresignDownloadTtlMinutes()))
			.getObjectRequest(getRequest)
			.build();

		PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
		return presigned.url().toString();
	}

	/**
	 * Verifies the object exists and returns its size in bytes via {@code ListObjectsV2}.
	 *
	 * <p>Avoids {@code HeadObject} / ranged {@code GetObject}, which AWS SDK v2 and some proxies
	 * (Cloudflare → MinIO) mishandle with unsigned checksum / Range headers.</p>
	 */
	public long verifyObjectSize(String objectKey) {
		try {
			ListObjectsV2Response listing = s3Client.listObjectsV2(ListObjectsV2Request.builder()
				.bucket(properties.getBucket())
				.prefix(objectKey)
				.maxKeys(2)
				.build());

			return listing.contents().stream()
				.filter(obj -> objectKey.equals(obj.key()))
				.map(S3Object::size)
				.findFirst()
				.orElseThrow(() -> new ResourceNotFoundException("Attachment object not found in storage"));
		} catch (NoSuchKeyException ex) {
			throw new ResourceNotFoundException("Attachment object not found in storage");
		} catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				throw new ResourceNotFoundException("Attachment object not found in storage");
			}
			if (ex.statusCode() == 403) {
				throw new ForbiddenException(
					"Object storage denied ListBucket/GetObject metadata for key '" + objectKey
						+ "' (403). Check that S3_ACCESS_KEY can list bucket '"
						+ properties.getBucket() + "'."
				);
			}
			if (ex.statusCode() == 400 && ex.getMessage() != null
				&& ex.getMessage().contains("not signed")) {
				throw new IllegalStateException(
					"Object storage rejected a signed request (unsigned headers). "
						+ "Point S3_ENDPOINT at MinIO directly (bypass reverse-proxy header injection), "
						+ "and keep AWS SDK checksum calculation at WHEN_REQUIRED.",
					ex
				);
			}
			throw ex;
		}
	}

	public void deleteObject(String objectKey) {
		s3Client.deleteObject(DeleteObjectRequest.builder()
			.bucket(properties.getBucket())
			.key(objectKey)
			.build());
	}
}
