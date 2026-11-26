package com.procureshield.service.ai;

import com.procureshield.entity.RiskLevel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskScoringServiceTest {

    private final RiskScoringService riskScoringService = new RiskScoringService();

    @ParameterizedTest
    @CsvSource({
            "15, 6, STANDARD, true, false, 100, LOW",
            "30, 12, STANDARD, true, false, 92, LOW",
            "60, 12, STANDARD, true, false, 82, LOW",
            "60, 24, LOW, true, false, 64, MEDIUM",
            "90, 24, LOW, true, false, 54, HIGH",
            "90, 24, NONE, false, true, 9, HIGH"
    })
    void evaluate_computesDeterministicScoreAndRiskLevel(
            int noticeDays,
            int termMonths,
            String liabilityCap,
            boolean residencyCompliant,
            boolean uncappedLiability,
            int expectedScore,
            RiskLevel expectedLevel
    ) {
        ExtractedContractFacts facts = new ExtractedContractFacts(
                noticeDays,
                termMonths,
                liabilityCap,
                residencyCompliant,
                uncappedLiability,
                List.of("Sample clause"),
                "Contract summary"
        );

        RiskScoringService.ScoredContractResult result = riskScoringService.evaluate(facts);

        assertEquals(expectedScore, result.overallScore());
        assertEquals(expectedLevel, result.riskLevel());
    }
}