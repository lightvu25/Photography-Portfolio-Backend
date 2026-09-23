package com.photography.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TurnstileService {

    @Value("${app.turnstile.enabled:true}")
    private boolean enabled;

    @Value("${app.turnstile.secret-key}")
    private String secretKey;

    public boolean verify(String token) {
        if (!enabled) {
            return true;
        }
        if (token == null || token.isEmpty()) {
            return false;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
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