package com.procureshield.entity;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static RiskLevel fromScore(int score) {
        if (score >= 80) {
            return LOW;
        }
        if (score >= 60) {
            return MEDIUM;
        }
        return HIGH;
    }
}