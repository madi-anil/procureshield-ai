package com.procureshield.service.ai;

import java.util.List;

public record ExtractedContractFacts(
        Integer autoRenewalNoticeDays,
        Integer autoRenewalTermMonths,
        String vendorLiabilityCap,
        Boolean dataResidencyCompliant,
        Boolean uncappedLiabilityDetected,
        List<String> flaggedClauses,
        String auditorSummary
) {
}