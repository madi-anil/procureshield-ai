package com.procureshield.service.ai;

import com.procureshield.config.AiComplianceProperties;
import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.Product;
import com.procureshield.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StuckAuditReaper {

    private final ProductRepository productRepository;
    private final AiComplianceProperties aiProperties;

    @Scheduled(fixedDelayString = "60000")
    @Transactional
    public void reapStuckAudits() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(aiProperties.getAuditTimeoutMinutes());
        List<Product> stuckProducts = productRepository.findByAuditStatusAndAuditStartedAtBefore(
                AuditStatus.AUDITING, cutoff);

        for (Product product : stuckProducts) {
            product.setAuditStatus(AuditStatus.MANUAL_REVIEW_REQUIRED);
            product.setAuditFailureReason("AUDIT_TIMEOUT");
            productRepository.save(product);
            log.warn("Marked stuck AI audit as MANUAL_REVIEW_REQUIRED: productId={}", product.getId());
        }
    }
}