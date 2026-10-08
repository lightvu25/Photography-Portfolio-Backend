package com.photography.backend.gallery.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PhotoReorderItem {

    @NotNull(message = "Photo id is required")
    private Long id;

    @NotNull(message = "Sort order is required")
    private Integer sortOrder;
}
