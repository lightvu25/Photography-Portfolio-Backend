package com.photography.backend.site.service;

import com.photography.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression: the admin settings form used to send {@code equipment: ""}; an
 * empty string is not valid JSON, so PostgreSQL rejected the write to the
 * jsonb column (SQLState 22P02) and the API answered 409.
 */
class EquipmentJsonTest {

    @Test
    void blankBecomesNull() {
        assertThat(EquipmentJson.normalize(null)).isNull();
        assertThat(EquipmentJson.normalize("")).isNull();
        assertThat(EquipmentJson.normalize("   ")).isNull();
    }

    @Test
    void validJsonIsKeptTrimmed() {
        assertThat(EquipmentJson.normalize(" [\"Sony A7IV\"] ")).isEqualTo("[\"Sony A7IV\"]");
        assertThat(EquipmentJson.normalize("{\"body\":\"A7IV\"}")).isEqualTo("{\"body\":\"A7IV\"}");
    }

    @Test
    void invalidJsonIsRejectedAsBadRequest() {
        assertThatThrownBy(() -> EquipmentJson.normalize("Sony A7IV"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus().value()).isEqualTo(400));
    }
}
