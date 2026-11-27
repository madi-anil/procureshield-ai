package com.procureshield.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "procureshield.ai")
@Getter
@Setter
public class AiComplianceProperties {
    private String model;
    private String apiKey;
    private double temperature = 0.0;
    private int maxInputChars = 60000;
    private int auditTimeoutMinutes = 10;
    private List<String> injectionPatterns = new ArrayList<>();
}