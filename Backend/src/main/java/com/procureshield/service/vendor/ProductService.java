package com.procureshield.service.vendor;

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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final R2StorageService r2StorageService;
    private final AiComplianceService aiComplianceService;

    @Transactional
    public ProductResponseDto createProduct(CreateProductRequestDto request) {
        String billing = (request.billingCycle() == null || request.billingCycle().isBlank())
                ? "ANNUAL"
                : request.billingCycle().trim().toUpperCase(Locale.ROOT);
        int renewalDays = request.renewalNoticeDays() != null ? request.renewalNoticeDays() : 30;

        Product product = Product.builder()
                .vendorName(request.vendorName().trim())
                .productName(request.productName().trim())
                .category(request.category().trim())
                .description(request.description())
                .pricePerSeat(request.pricePerSeat())
                .billingCycle(billing)
                .renewalNoticeDays(renewalDays)
                .auditStatus(AuditStatus.PENDING_UPLOAD)
                .isActive(true)
                .build();

        return toResponseDto(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDto> listProducts(
            String category,
            RiskLevel riskLevel,
            AuditStatus auditStatus,
            String search,
            Pageable pageable
    ) {
        return productRepository.searchActiveCatalog(category, riskLevel, auditStatus, search, pageable)
                .map(this::toResponseDto);
    }

    @Transactional(readOnly = true)
    public ProductResponseDto getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        return toResponseDto(product);
    }

    @Transactional
    public ProductResponseDto updateProduct(Long id, UpdateProductRequestDto request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));

        product.setVendorName(request.vendorName().trim());
        product.setProductName(request.productName().trim());
        product.setCategory(request.category().trim());
        product.setDescription(request.description());
        product.setPricePerSeat(request.pricePerSeat());
        if (request.billingCycle() != null && !request.billingCycle().isBlank()) {
            product.setBillingCycle(request.billingCycle().trim().toUpperCase(Locale.ROOT));
        }
        if (request.renewalNoticeDays() != null) {
            product.setRenewalNoticeDays(request.renewalNoticeDays());
        }
        if (request.isActive() != null) {
            product.setIsActive(request.isActive());
        }

        return toResponseDto(productRepository.save(product));
    }

    @Transactional
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setIsActive(false);
        productRepository.save(product);
    }

    @Transactional
    public SlaUploadResponseDto uploadSlaAndTriggerAudit(Long productId, MultipartFile file) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        String r2Key = r2StorageService.uploadCompliancePdf(productId, "sla", file);

        product.setSlaPdfR2Key(r2Key);
        product.setAuditStatus(AuditStatus.AUDITING);
        product.setAiRiskScore(null);
        product.setAiRiskLevel(null);
        product.setAiRiskScorecard(null);
        product.setAuditFailureReason(null);
        productRepository.save(product);

        aiComplianceService.auditSlaAsync(productId, r2Key);

        return new SlaUploadResponseDto(
                productId,
                AuditStatus.AUDITING,
                r2Key,
                "SLA uploaded; asynchronous AI compliance audit started."
        );
    }

    @Transactional
    public AssetUploadResponseDto uploadAsset(Long productId, String type, MultipartFile file) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("LOGO".equals(normalizedType)) {
            String key = r2StorageService.uploadLogo(productId, file);
            String url = r2StorageService.generatePresignedGetUrl(key).url();
            product.setLogoR2Url(url);
            productRepository.save(product);
            return new AssetUploadResponseDto(productId, "LOGO", key, url);
        }

        if ("SOC2_CERT".equals(normalizedType)) {
            String key = r2StorageService.uploadCompliancePdf(productId, "soc2", file);
            String url = r2StorageService.generatePresignedGetUrl(key).url();
            product.setSoc2CertR2Key(key);
            productRepository.save(product);
            return new AssetUploadResponseDto(productId, "SOC2_CERT", key, url);
        }

        throw new IllegalArgumentException("Unsupported asset type: " + type + ". Expected LOGO or SOC2_CERT.");
    }

    @Transactional(readOnly = true)
    public RiskScorecardResponseDto getRiskScorecard(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        return new RiskScorecardResponseDto(
                product.getId(),
                product.getProductName(),
                product.getVendorName(),
                product.getAuditStatus(),
                product.getAuditFailureReason(),
                product.getAiRiskScore(),
                product.getAiRiskLevel(),
                product.getAiRiskScorecard()
        );
    }

    @Transactional
    public SlaUploadResponseDto retryAudit(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (product.getSlaPdfR2Key() == null || product.getSlaPdfR2Key().isBlank()) {
            throw new IllegalArgumentException("Cannot retry audit: no SLA PDF uploaded for product " + productId);
        }

        if (product.getAuditStatus() != AuditStatus.FAILED
                && product.getAuditStatus() != AuditStatus.MANUAL_REVIEW_REQUIRED) {
            throw new IllegalArgumentException(
                    "Audit retry is only allowed when status is FAILED or MANUAL_REVIEW_REQUIRED"
            );
        }

        product.setAuditStatus(AuditStatus.AUDITING);
        product.setAuditFailureReason(null);
        productRepository.save(product);

        aiComplianceService.auditSlaAsync(productId, product.getSlaPdfR2Key());

        return new SlaUploadResponseDto(
                productId,
                AuditStatus.AUDITING,
                product.getSlaPdfR2Key(),
                "AI SLA risk audit re-queued successfully."
        );
    }

    private ProductResponseDto toResponseDto(Product product) {
        return new ProductResponseDto(
                product.getId(),
                product.getVendorName(),
                product.getProductName(),
                product.getCategory(),
                product.getDescription(),
                product.getPricePerSeat(),
                product.getBillingCycle(),
                product.getLogoR2Url(),
                product.getSoc2CertR2Key(),
                product.getSlaPdfR2Key(),
                product.getAuditStatus(),
                product.getAuditFailureReason(),
                product.getAiRiskScore(),
                product.getAiRiskLevel(),
                product.getRenewalNoticeDays(),
                product.getIsActive(),
                product.getCreatedAt()
        );
    }
}