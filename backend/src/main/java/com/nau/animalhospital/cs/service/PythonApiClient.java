package com.nau.animalhospital.cs.service;

import com.nau.animalhospital.cs.config.AppProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PythonApiClient {
    private final RestTemplate restTemplate;
    private final AppProperties appProperties;

    @CircuitBreaker(name = "pythonAsk", fallbackMethod = "askFallback")
    public Map<String, Object> ask(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                appProperties.getPythonAiUrl(),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> m = resp.getBody();
        return m != null ? m : new HashMap<>();
    }

    public Map<String, Object> askFallback(Map<String, Object> body, Throwable t) {
        return Map.of("answer", "系统繁忙，请稍后再试");
    }

    @CircuitBreaker(name = "pythonIndex", fallbackMethod = "indexIncrementalFallback")
    public ResponseEntity<Map<String, Object>> indexIncremental(HttpEntity<Map<String, Object>> entity) {
        String url = appProperties.getPythonIncrementalIndexUrl();
        return restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public ResponseEntity<Map<String, Object>> indexIncrementalFallback(HttpEntity<Map<String, Object>> entity, Throwable t) {
        return ResponseEntity.status(503).build();
    }

    @CircuitBreaker(name = "pythonIndex", fallbackMethod = "deleteDocumentIndexFallback")
    public void deleteDocumentIndex(Long tenantId, Long docId) {
        String base = appProperties.getPythonDeleteDocumentIndexUrl();
        if (base == null || base.isBlank()) {
            return;
        }
        restTemplate.exchange(base + "/" + tenantId + "/" + docId, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
    }

    public void deleteDocumentIndexFallback(Long tenantId, Long docId, Throwable t) {
    }

    @CircuitBreaker(name = "pythonIndex", fallbackMethod = "rebuildFallback")
    public ResponseEntity<Map<String, Object>> rebuild(HttpEntity<Map<String, Object>> entity) {
        String url = appProperties.getPythonIndexRebuildUrl();
        return restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public ResponseEntity<Map<String, Object>> rebuildFallback(HttpEntity<Map<String, Object>> entity, Throwable t) {
        return ResponseEntity.status(503).build();
    }
}
