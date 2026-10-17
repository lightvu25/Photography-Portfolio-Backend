package com.photography.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class TurnstileService {

    @Value("${app.turnstile.enabled:true}")
    private boolean enabled;

    // Optional default: TURNSTILE_ENABLED=false must boot without a secret.
    // When enabled and the secret is missing, fail fast at startup with an
    // explicit message instead of a placeholder error or silent 400s.
    @Value("${app.turnstile.secret-key:}")
    private String secretKey;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        if (enabled && (secretKey == null || secretKey.isBlank())) {
            throw new IllegalStateException(
                    "TURNSTILE_SECRET_KEY must be set when TURNSTILE_ENABLED=true");
        }
        // Bounded timeouts — an unreachable Cloudflare endpoint must not pin a
        // request thread forever (default RestTemplate waits indefinitely).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean verify(String token) {
        if (!enabled) {
            return true;
        }
        if (token == null || token.isEmpty()) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("secret", secretKey);
            map.add("response", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://challenges.cloudflare.com/turnstile/v0/siteverify",
                    request,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                return true;
            }
            log.warn("Turnstile verification failed: {}", body);
            return false;
        } catch (Exception e) {
            log.error("Error verifying turnstile token", e);
            return false;
        }
    }
}