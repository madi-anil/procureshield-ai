package com.procureshield.controller;

import com.procureshield.dto.ProductDtos.AssetUploadResponseDto;
import com.procureshield.dto.ProductDtos.CreateProductRequestDto;
import com.procureshield.dto.ProductDtos.ProductResponseDto;
import com.procureshield.dto.ProductDtos.RiskScorecardResponseDto;
import com.procureshield.dto.ProductDtos.SlaUploadResponseDto;
import com.procureshield.dto.ProductDtos.UpdateProductRequestDto;
import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.RiskLevel;
import com.procureshield.service.vendor.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    @Operation(summary = "Create a SaaS catalog product")
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody CreateProductRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @GetMapping
    @Operation(summary = "List active SaaS catalog products with optional filters")
    public ResponseEntity<Page<ProductResponseDto>> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) AuditStatus auditStatus,
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        return ResponseEntity.ok(productService.listProducts(category, riskLevel, auditStatus, search, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a SaaS catalog product by ID")
    public ResponseEntity<ProductResponseDto> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    @Operation(summary = "Update a SaaS catalog product")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequestDto request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    @Operation(summary = "Deactivate a SaaS catalog product")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/upload-sla", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    @Operation(summary = "Upload vendor SLA PDF to R2 and trigger async AI compliance audit")
    public ResponseEntity<SlaUploadResponseDto> uploadSla(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(productService.uploadSlaAndTriggerAudit(id, file));
    }

    @PostMapping(value = "/{id}/upload-asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    @Operation(summary = "Upload product logo or SOC2 certificate to R2")
    public ResponseEntity<AssetUploadResponseDto> uploadAsset(
            @PathVariable Long id,
            @RequestParam("type") String type,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(productService.uploadAsset(id, type, file));
    }

    @GetMapping("/{id}/risk-scorecard")
    @Operation(summary = "Get AI compliance risk scorecard and audit status for a product")
    public ResponseEntity<RiskScorecardResponseDto> getRiskScorecard(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getRiskScorecard(id));
    }

    @PostMapping("/{id}/retry-audit")
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    @Operation(summary = "Retry failed or manual-review AI SLA compliance audit")
    public ResponseEntity<SlaUploadResponseDto> retryAudit(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(productService.retryAudit(id));
    }
}