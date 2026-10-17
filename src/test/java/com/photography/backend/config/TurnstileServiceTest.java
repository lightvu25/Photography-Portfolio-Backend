package com.photography.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for TurnstileService — plain JUnit, no Spring context.
 * Covers: enabled/disabled modes, missing secret fail-fast, valid/invalid
 * token responses, and controlled failure on Cloudflare errors.
 */
class TurnstileServiceTest {

    private TurnstileService service(boolean enabled, String secretKey) {
        TurnstileService s = new TurnstileService();
        ReflectionTestUtils.setField(s, "enabled", enabled);
        ReflectionTestUtils.setField(s, "secretKey", secretKey);
        return s;
    }

    private MockRestServiceServer mockServer(TurnstileService s) {
        RestTemplate rt = (RestTemplate) ReflectionTestUtils.getField(s, "restTemplate");
        assertNotNull(rt, "restTemplate must be initialized in @PostConstruct");
        return MockRestServiceServer.createServer(rt);
    }

    @Test
    void disabledModeBootsWithoutSecretAndSkipsVerification() {
        TurnstileService s = service(false, "");
        assertDoesNotThrow(s::init);
        assertTrue(s.verify(null));
        assertTrue(s.verify("anything"));
    }

    @Test
    void enabledWithoutSecretFailsFastWithExplicitMessage() {
        TurnstileService s = service(true, "");
        IllegalStateException e = assertThrows(IllegalStateException.class, s::init);
        assertTrue(e.getMessage().contains("TURNSTILE_SECRET_KEY"));
    }

    @Test
    void enabledWithBlankSecretAlsoFailsFast() {
        TurnstileService s = service(true, "   ");
        assertThrows(IllegalStateException.class, s::init);
    }

    @Test
    void configuredTimeoutsAreExplicitAndBounded() {
        TurnstileService s = service(true, "secret");
        s.init();
        RestTemplate rt = (RestTemplate) ReflectionTestUtils.getField(s, "restTemplate");
        SimpleClientHttpRequestFactory f = (SimpleClientHttpRequestFactory) rt.getRequestFactory();
        assertEquals(5_000, ReflectionTestUtils.getField(f, "connectTimeout"));
        assertEquals(10_000, ReflectionTestUtils.getField(f, "readTimeout"));
    }

    @Test
    void blankTokenFailsWithoutCallingCloudflare() {
        TurnstileService s = service(true, "secret");
        s.init();
        MockRestServiceServer server = mockServer(s);
        assertFalse(s.verify(null));
        assertFalse(s.verify(""));
        server.verify(); // proves no HTTP request was made
    }

    @Test
    void cloudflareSuccessTrueVerifies() {
        TurnstileService s = service(true, "secret");
        s.init();
        MockRestServiceServer server = mockServer(s);
        server.expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        assertTrue(s.verify("valid-token"));
        server.verify();
    }

    @Test
    void cloudflareSuccessFalseFails() {
        TurnstileService s = service(true, "secret");
        s.init();
        MockRestServiceServer server = mockServer(s);
        server.expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));
        assertFalse(s.verify("bad-token"));
        server.verify();
    }

    @Test
    void cloudflareUnreachableFailsWithoutBypassing() {
        TurnstileService s = service(true, "secret");
        s.init();
        MockRestServiceServer server = mockServer(s);
        server.expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out");
                });
        assertFalse(s.verify("any-token"));
        server.verify();
    }
}
