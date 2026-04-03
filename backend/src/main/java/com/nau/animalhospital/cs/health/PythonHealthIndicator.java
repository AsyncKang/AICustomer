package com.nau.animalhospital.cs.health;

import com.nau.animalhospital.cs.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component("python")
@RequiredArgsConstructor
public class PythonHealthIndicator implements HealthIndicator {
    private final AppProperties appProperties;
    @Qualifier("healthRestTemplate")
    private final RestTemplate healthRestTemplate;

    @Override
    public Health health() {
        String url = appProperties.getPythonHealthUrl();
        if (url == null || url.isBlank()) {
            return Health.unknown().withDetail("reason", "pythonHealthUrl_empty").build();
        }
        try {
            ResponseEntity<Map<String, Object>> resp = healthRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || body == null) {
                return Health.down().withDetail("http", "bad_response").build();
            }
            Object s = body.get("status");
            Status st = s != null && "UP".equalsIgnoreCase(String.valueOf(s)) ? Status.UP : Status.DOWN;
            Health.Builder b = Health.status(st);
            for (Map.Entry<String, Object> e : body.entrySet()) {
                b.withDetail(e.getKey(), e.getValue());
            }
            return b.build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
