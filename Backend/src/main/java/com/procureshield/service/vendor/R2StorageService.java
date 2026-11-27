package com.procureshield.service.vendor;

import com.procureshield.config.R2StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class R2StorageService {

    private static final long MAX_PDF_BYTES = 10L * 1024 * 1024;   // 10 MB
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;   // 2 MB
    private static final Set<String> ALLOWED_LOGO_TYPES = Set.of("image/png", "image/jpeg");
    private static final Duration PRESIGNED_TTL = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2StorageProperties r2Properties;

    public String uploadCompliancePdf(Long productId, String docType, MultipartFile file) {
        validateConfigured();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded PDF file must not be empty.");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("Compliance PDF exceeds maximum size of 10 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded PDF bytes.", ex);
        }

        if (!hasPdfMagicHeader(bytes)) {
            throw new IllegalArgumentException("Invalid file format: file must be a valid PDF.");
        }

        String sanitizedType = docType.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        String objectKey = String.format("products/%d/%s-%s.pdf", productId, sanitizedType, UUID.randomUUID());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .contentType("application/pdf")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        log.info("Uploaded compliance PDF to R2: productId={}, docType={}, key={}", productId, sanitizedType, objectKey);
        return objectKey;
    }

    public String uploadLogo(Long productId, MultipartFile file) {
        validateConfigured();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file must not be empty.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("Logo image exceeds maximum size of 2 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_LOGO_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Logo must be PNG or JPEG.");
        }

        String ext = "image/png".equalsIgnoreCase(contentType) ? "png" : "jpg";
        String objectKey = String.format("products/%d/logo-%s.%s", productId, UUID.randomUUID(), ext);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to upload product logo.", ex);
        }

        return objectKey;
    }

    public byte[] downloadObjectBytes(String objectKey) {
        validateConfigured();
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .build();

        ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getRequest);
        return objectBytes.asByteArray();
    }

    public PresignedUrlResult generatePresignedGetUrl(String objectKey) {
        validateConfigured();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return new PresignedUrlResult(presigned.url().toString(), Instant.now().plus(PRESIGNED_TTL));
    }

    private void validateConfigured() {
        if (r2Properties.getAccessKey() == null || r2Properties.getAccessKey().isBlank()
                || r2Properties.getSecretKey() == null || r2Properties.getSecretKey().isBlank()) {
            throw new IllegalStateException("Cloudflare R2 credentials are not configured.");
        }
    }

    private boolean hasPdfMagicHeader(byte[] bytes) {
        if (bytes.length < 5) {
            return false;
        }
        String header = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
        return "%PDF-".equals(header);
    }

    public record PresignedUrlResult(String url, Instant expiresAt) {
    }
}