package com.photography.backend.site.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photography.backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Normalises the {@code equipment} setting before it reaches the {@code jsonb}
 * column. PostgreSQL rejects an empty string as JSON (SQLState 22P02), so a
 * blank value must be stored as NULL, and anything else must be valid JSON —
 * otherwise the client gets a clear 400 instead of an opaque database error.
 */
public final class EquipmentJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EquipmentJson() {
    }

    /** @return null for null/blank input, the trimmed JSON text otherwise. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            MAPPER.readTree(trimmed);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    "Trường thiết bị (equipment) phải là JSON hợp lệ hoặc để trống",
                    "INVALID_EQUIPMENT_JSON", HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }
}
