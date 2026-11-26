package com.procureshield.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "procureshield.r2")
@Getter
@Setter
public class R2StorageProperties {
    private String accountId;
    private String accessKey;
    private String secretKey;
    private String bucketName = "procureshield-docs";
    private String endpointUrl;

    public String resolveEndpointUrl() {
        if (endpointUrl != null && !endpointUrl.isBlank()) {
            return endpointUrl.trim();
        }
        if (accountId != null && !accountId.isBlank()) {
            return "https://" + accountId.trim() + ".r2.cloudflarestorage.com";
        }
        return "http://localhost:9000";
    }
}