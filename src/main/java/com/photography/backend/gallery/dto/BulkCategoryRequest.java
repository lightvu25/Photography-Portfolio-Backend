package com.photography.backend.gallery.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class BulkCategoryRequest {

    @NotEmpty(message = "photoIds must not be empty")
    private Set<Long> photoIds;

    /** Categories added to every selected photo. */
    private Set<Long> addCategoryIds;

    /** Categories removed from every selected photo. */
    private Set<Long> removeCategoryIds;
}
