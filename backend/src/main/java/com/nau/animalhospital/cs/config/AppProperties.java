package com.nau.animalhospital.cs.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String pythonAiUrl;
    private String pythonHealthUrl;
    private String pythonIncrementalIndexUrl;
    private String pythonDeleteDocumentIndexUrl;
    private String pythonIndexRebuildUrl;
    private String uploadDir;
    private Index index = new Index();
    private Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Index {
        private int maxRetryCount = 5;
        private int retryIntervalMinutes = 2;
        private int httpAttempts = 3;
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private Long expireMinutes;
    }
}
