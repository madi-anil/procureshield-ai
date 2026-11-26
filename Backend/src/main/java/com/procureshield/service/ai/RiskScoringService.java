package com.procureshield.service.ai;

import com.procureshield.entity.RiskLevel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RiskScoringService {

    public static final String RUBRIC_VERSION = "1.0";

    public ScoredContractResult evaluate(ExtractedContractFacts facts) {
        validateFacts(facts);

        int penalties = 0;

        // 1. Auto-renewal notice window penalty
        int noticeDays = facts.autoRenewalNoticeDays();
        if (noticeDays >= 90) {
            penalties += 25;
        } else if (noticeDays >= 60) {
            penalties += 15;
        } else if (noticeDays >= 30) {
            penalties += 5;
        }

        // 2. Auto-renewal lock-in term penalty
        int termMonths = facts.autoRenewalTermMonths();
        if (termMonths >= 24) {
            penalties += 6;
        } else if (termMonths >= 12) {
            penalties += 3;
        }

        // 3. Vendor liability cap & uncapped customer liability penalties
        String cap = facts.vendorLiabilityCap().toUpperCase();
        if ("NONE".equals(cap)) {
            penalties += 25;
        } else if ("LOW".equals(cap)) {
            penalties += 15;
        }

        if (Boolean.TRUE.equals(facts.uncappedLiabilityDetected())) {
            penalties += 20;
        }

        // 4. Data residency non-compliance penalty
        if (Boolean.FALSE.equals(facts.dataResidencyCompliant())) {
            penalties += 15;
        }

        int overallScore = Math.max(1, Math.min(100, 100 - penalties));
        RiskLevel riskLevel = RiskLevel.fromScore(overallScore);

        Map<String, Object> scorecard = new LinkedHashMap<>();
        scorecard.put("overallScore", overallScore);
        scorecard.put("riskLevel", riskLevel.name());
        scorecard.put("autoRenewalNoticeDays", noticeDays);
        scorecard.put("dataResidencyCompliant", facts.dataResidencyCompliant());
        scorecard.put("uncappedLiabilityDetected", facts.uncappedLiabilityDetected());
        scorecard.put("autoRenewalTermMonths", termMonths);
        scorecard.put("vendorLiabilityCap", cap);
        scorecard.put("rubricVersion", RUBRIC_VERSION);
        scorecard.put("flaggedClauses", facts.flaggedClauses());
        scorecard.put("auditorSummary", facts.auditorSummary());

        return new ScoredContractResult(overallScore, riskLevel, scorecard);
    }

    private void validateFacts(ExtractedContractFacts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("Extracted contract facts cannot be null");
        }
        if (facts.autoRenewalNoticeDays() == null || facts.autoRenewalNoticeDays() < 0 || facts.autoRenewalNoticeDays() > 365) {
            throw new IllegalArgumentException("Invalid autoRenewalNoticeDays");
        }
        if (facts.autoRenewalTermMonths() == null || facts.autoRenewalTermMonths() < 0 || facts.autoRenewalTermMonths() > 120) {
            throw new IllegalArgumentException("Invalid autoRenewalTermMonths");
        }
        if (facts.vendorLiabilityCap() == null || !List.of("NONE", "LOW", "STANDARD", "UNCAPPED").contains(facts.vendorLiabilityCap().toUpperCase())) {
            throw new IllegalArgumentException("Invalid vendorLiabilityCap");
        }
        if (facts.dataResidencyCompliant() == null || facts.uncappedLiabilityDetected() == null) {
            throw new IllegalArgumentException("Compliance boolean flags cannot be null");
        }
        if (facts.flaggedClauses() == null || facts.flaggedClauses().size() > 20) {
            throw new IllegalArgumentException("Invalid flaggedClauses list");
        }
        if (facts.auditorSummary() == null || facts.auditorSummary().isBlank() || facts.auditorSummary().length() > 1000) {
            throw new IllegalArgumentException("Invalid auditorSummary");
        }
    }

    public record ScoredContractResult(
            int overallScore,
            RiskLevel riskLevel,
            Map<String, Object> scorecard
    ) {
    }
}