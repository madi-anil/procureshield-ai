package com.procureshield.service.vendor;

import static org.mockito.Mockito.times;
import com.procureshield.dto.ProductDtos.AssetUploadResponseDto;
import com.procureshield.dto.ProductDtos.CreateProductRequestDto;
import com.procureshield.dto.ProductDtos.ProductResponseDto;
import com.procureshield.dto.ProductDtos.RiskScorecardResponseDto;
import com.procureshield.dto.ProductDtos.SlaUploadResponseDto;
import com.procureshield.dto.ProductDtos.UpdateProductRequestDto;
import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.Product;
import com.procureshield.entity.RiskLevel;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.ProductRepository;
import com.procureshield.service.ai.AiComplianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private R2StorageService r2StorageService;

    @Mock
    private AiComplianceService aiComplianceService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, r2StorageService, aiComplianceService);
    }

    @Test
    void createProduct_defaultsBillingAndRenewalAndSetsPendingUpload() {
        CreateProductRequestDto req = new CreateProductRequestDto(
                "Datadog Inc.",
                "Datadog Enterprise APM",
                "Observability",
                "Monitoring platform",
                new BigDecimal("300.00"),
                null,
                null
        );

        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(10L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        ProductResponseDto res = productService.createProduct(req);

        assertEquals(10L, res.id());
        assertEquals("ANNUAL", res.billingCycle());
        assertEquals(30, res.renewalNoticeDays());
        assertEquals(AuditStatus.PENDING_UPLOAD, res.auditStatus());
        assertEquals(Boolean.TRUE, res.isActive());
    }

    @Test
    void listAndGetAndUpdateAndDeactivateProduct_workAsExpected() {
        Product existing = Product.builder()
                .id(10L)
                .vendorName("Datadog Inc.")
                .productName("Datadog Enterprise APM")
                .category("Observability")
                .pricePerSeat(new BigDecimal("300.00"))
                .billingCycle("ANNUAL")
                .renewalNoticeDays(90)
                .auditStatus(AuditStatus.READY)
                .aiRiskScore(54)
                .aiRiskLevel(RiskLevel.HIGH)
                .isActive(true)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.searchActiveCatalog(null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(existing), pageable, 1));
        when(productRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Page<ProductResponseDto> page = productService.listProducts(null, null, null, null, pageable);
        assertEquals(1, page.getTotalElements());

        ProductResponseDto fetched = productService.getProduct(10L);
        assertEquals("Datadog Enterprise APM", fetched.productName());

        UpdateProductRequestDto updateReq = new UpdateProductRequestDto(
                "Datadog Inc.",
                "Datadog APM Pro",
                "Observability",
                "Updated desc",
                new BigDecimal("350.00"),
                "MONTHLY",
                60,
                true
        );
        ProductResponseDto updated = productService.updateProduct(10L, updateReq);
        assertEquals("Datadog APM Pro", updated.productName());
        assertEquals("MONTHLY", updated.billingCycle());
        assertEquals(60, updated.renewalNoticeDays());
        assertEquals(new BigDecimal("350.00"), updated.pricePerSeat());

        productService.deactivateProduct(10L);
        assertFalse(existing.getIsActive());
        verify(productRepository, times(2)).save(existing);
    }

    @Test
    void getProduct_throwsNotFoundWhenMissing() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> productService.getProduct(999L));
    }

    @Test
    void uploadSlaAndTriggerAudit_clearsPriorScoreAndStartsAsyncAudit() {
        Product product = Product.builder()
                .id(10L)
                .productName("Datadog Enterprise APM")
                .vendorName("Datadog Inc.")
                .auditStatus(AuditStatus.READY)
                .aiRiskScore(54)
                .aiRiskLevel(RiskLevel.HIGH)
                .aiRiskScorecard(Map.of("overallScore", 54))
                .build();

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "sla.pdf", "application/pdf", "%PDF-1.4 test".getBytes()
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(r2StorageService.uploadCompliancePdf(10L, "sla", pdf)).thenReturn("products/10/sla-123.pdf");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        SlaUploadResponseDto res = productService.uploadSlaAndTriggerAudit(10L, pdf);

        assertEquals(AuditStatus.AUDITING, res.auditStatus());
        assertEquals("products/10/sla-123.pdf", product.getSlaPdfR2Key());
        assertNull(product.getAiRiskScore());
        assertNull(product.getAiRiskLevel());
        assertNull(product.getAiRiskScorecard());
        verify(aiComplianceService).auditSlaAsync(10L, "products/10/sla-123.pdf");
    }

    @Test
    void uploadAsset_supportsLogoAndSoc2AndRejectsInvalidType() {
        Product product = Product.builder().id(10L).build();
        MockMultipartFile file = new MockMultipartFile(
                "file", "asset.bin", "application/pdf", "%PDF-1.4 test".getBytes()
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(r2StorageService.uploadLogo(10L, file)).thenReturn("products/10/logo-1.png");
        when(r2StorageService.uploadCompliancePdf(10L, "soc2", file)).thenReturn("products/10/soc2-1.pdf");
        when(r2StorageService.generatePresignedGetUrl(any()))
                .thenReturn(new R2StorageService.PresignedUrlResult("https://r2.example.com/obj", Instant.now().plusSeconds(900)));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetUploadResponseDto logoRes = productService.uploadAsset(10L, "LOGO", file);
        assertEquals("LOGO", logoRes.type());
        assertNotNull(product.getLogoR2Url());

        AssetUploadResponseDto soc2Res = productService.uploadAsset(10L, "SOC2_CERT", file);
        assertEquals("SOC2_CERT", soc2Res.type());
        assertEquals("products/10/soc2-1.pdf", product.getSoc2CertR2Key());

        assertThrows(IllegalArgumentException.class, () -> productService.uploadAsset(10L, "UNKNOWN", file));
    }

    @Test
    void getRiskScorecardAndRetryAudit_handleValidAndInvalidStates() {
        Product failedProduct = Product.builder()
                .id(10L)
                .productName("Datadog Enterprise APM")
                .vendorName("Datadog Inc.")
                .slaPdfR2Key("products/10/sla-123.pdf")
                .auditStatus(AuditStatus.FAILED)
                .auditFailureReason("NO_EXTRACTABLE_TEXT")
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(failedProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskScorecardResponseDto scorecard = productService.getRiskScorecard(10L);
        assertEquals(AuditStatus.FAILED, scorecard.auditStatus());
        assertEquals("NO_EXTRACTABLE_TEXT", scorecard.auditFailureReason());

        SlaUploadResponseDto retry = productService.retryAudit(10L);
        assertEquals(AuditStatus.AUDITING, retry.auditStatus());
        assertNull(failedProduct.getAuditFailureReason());
        verify(aiComplianceService).auditSlaAsync(10L, "products/10/sla-123.pdf");

        failedProduct.setAuditStatus(AuditStatus.READY);
        assertThrows(IllegalArgumentException.class, () -> productService.retryAudit(10L));

        failedProduct.setSlaPdfR2Key(null);
        failedProduct.setAuditStatus(AuditStatus.FAILED);
        assertThrows(IllegalArgumentException.class, () -> productService.retryAudit(10L));
    }
}