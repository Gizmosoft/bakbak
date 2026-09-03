package uk.deadcatlab.bakbak.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible object storage settings (MinIO, Cloudflare R2, AWS S3).
 *
 * <p>Switch providers by changing endpoint and credentials — no code changes required.</p>
 */
@ConfigurationProperties(prefix = "bakbak.storage")
public class StorageProperties {

	private String endpoint;
	private String region = "us-east-1";
	private String accessKey;
	private String secretKey;
	private String bucket = "gup-media";
	private int presignUploadTtlMinutes = 15;
	private int presignDownloadTtlMinutes = 15;
	private long maxSizeBytes = 25L * 1024 * 1024;
	private List<String> allowedMimeTypes = List.of(
		"image/jpeg",
		"image/png",
		"image/gif",
		"image/webp",
		"video/mp4",
		"video/quicktime",
		"audio/mpeg",
		"audio/mp4",
		"audio/ogg",
		"application/pdf"
	);
	private int pendingTtlHours = 24;

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public int getPresignUploadTtlMinutes() {
		return presignUploadTtlMinutes;
	}

	public void setPresignUploadTtlMinutes(int presignUploadTtlMinutes) {
		this.presignUploadTtlMinutes = presignUploadTtlMinutes;
	}

	public int getPresignDownloadTtlMinutes() {
		return presignDownloadTtlMinutes;
	}

	public void setPresignDownloadTtlMinutes(int presignDownloadTtlMinutes) {
		this.presignDownloadTtlMinutes = presignDownloadTtlMinutes;
	}

	public long getMaxSizeBytes() {
		return maxSizeBytes;
	}

	public void setMaxSizeBytes(long maxSizeBytes) {
		this.maxSizeBytes = maxSizeBytes;
	}

	public List<String> getAllowedMimeTypes() {
		return allowedMimeTypes;
	}

	public void setAllowedMimeTypes(List<String> allowedMimeTypes) {
		this.allowedMimeTypes = allowedMimeTypes;
	}

	public int getPendingTtlHours() {
		return pendingTtlHours;
	}

	public void setPendingTtlHours(int pendingTtlHours) {
		this.pendingTtlHours = pendingTtlHours;
	}
}
