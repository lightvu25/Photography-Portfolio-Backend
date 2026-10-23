package com.photography.backend.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** DataIntegrityViolation mapping: malformed data → 400, real conflicts stay 409. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("PUT", "/api/admin/site"));

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void invalidJsonSqlStateIsBadRequest() {
        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("x", new SQLException("invalid input syntax for type json", "22P02")),
                request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response).get("error")).isEqualTo("INVALID_DATA");
    }

    @Test
    void valueTooLongSqlStateIsBadRequest() {
        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("x", new SQLException("value too long", "22001")), request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void uniqueViolationStaysConflict() {
        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("x", new SQLException("duplicate key", "23505")), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response).get("error")).isEqualTo("CONFLICT");
    }

    @Test
    void unknownCauseStaysConflictAndLeaksNothing() {
        ResponseEntity<?> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("secret detail"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response).values().toString()).doesNotContain("secret detail");
    }
}
