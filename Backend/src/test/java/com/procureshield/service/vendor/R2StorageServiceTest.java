package com.procureshield.service.vendor;

import com.procureshield.config.R2StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private R2StorageProperties properties;
    private R2StorageService r2StorageService;

    @BeforeEach
    void setUp() {
        properties = new R2StorageProperties();
        properties.setAccountId("test-account");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setBucketName("procureshield-docs");

        r2StorageService = new R2StorageService(s3Client, s3Presigner, properties);
    }

    @Test
    void uploadCompliancePdf_uploadsValidPdfAndRejectsInvalidInputs() {
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "sla.pdf", "application/pdf", "%PDF-1.7 sample content".getBytes()
        );

        String key = r2StorageService.uploadCompliancePdf(5L, "sla", validPdf);
        assertTrue(key.startsWith("products/5/sla-"));
        assertTrue(key.endsWith(".pdf"));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        MockMultipartFile notPdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "NOT-A-PDF".getBytes()
        );
        assertThrows(IllegalArgumentException.class, () -> r2StorageService.uploadCompliancePdf(5L, "sla", notPdf));

        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> r2StorageService.uploadCompliancePdf(5L, "sla", emptyFile));

        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = '%'; oversized[1] = 'P'; oversized[2] = 'D'; oversized[3] = 'F'; oversized[4] = '-';
        MockMultipartFile tooLarge = new MockMultipartFile("file", "big.pdf", "application/pdf", oversized);
        assertThrows(IllegalArgumentException.class, () -> r2StorageService.uploadCompliancePdf(5L, "sla", tooLarge));
    }

    @Test
    void uploadLogo_uploadsPngAndJpegAndRejectsInvalid() {
        MockMultipartFile validPng = new MockMultipartFile(
                "file", "logo.png", "image/png", new byte[]{1, 2, 3, 4}
        );
        assertTrue(r2StorageService.uploadLogo(5L, validPng).endsWith(".png"));

        MockMultipartFile validJpeg = new MockMultipartFile(
                "file", "logo.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}
        );
        assertTrue(r2StorageService.uploadLogo(5L, validJpeg).endsWith(".jpg"));

        MockMultipartFile badType = new MockMultipartFile(
                "file", "logo.gif", "image/gif", new byte[]{1, 2, 3}
        );
        assertThrows(IllegalArgumentException.class, () -> r2StorageService.uploadLogo(5L, badType));

        MockMultipartFile oversizedLogo = new MockMultipartFile(
                "file", "logo.png", "image/png", new byte[2 * 1024 * 1024 + 1]
        );
        assertThrows(IllegalArgumentException.class, () -> r2StorageService.uploadLogo(5L, oversizedLogo));
    }

    @Test
    void downloadAndPresign_workAndThrowWhenCredentialsMissing() throws Exception {
        byte[] payload = "%PDF-1.4 bytes".getBytes();
        GetObjectResponse response = GetObjectResponse.builder().contentLength((long) payload.length).build();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(response, payload));

        assertArrayEquals(payload, r2StorageService.downloadObjectBytes("products/5/sla-1.pdf"));

        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://r2.example.com/products/5/sla-1.pdf").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        R2StorageService.PresignedUrlResult urlResult = r2StorageService.generatePresignedGetUrl("products/5/sla-1.pdf");
        assertNotNull(urlResult.url());
        assertEquals("https://r2.example.com/products/5/sla-1.pdf", urlResult.url());

        properties.setAccessKey("");
        assertThrows(IllegalStateException.class, () -> r2StorageService.downloadObjectBytes("products/5/sla-1.pdf"));
    }
}