package com.photography.backend.gallery.dto;

import com.photography.backend.gallery.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String slug;

    private Integer sortOrder;

    private CategoryType type;
}
