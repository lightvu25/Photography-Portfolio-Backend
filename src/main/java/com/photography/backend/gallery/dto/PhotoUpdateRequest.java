package com.photography.backend.gallery.dto;

import lombok.Data;

import java.util.Set;

@Data
public class PhotoUpdateRequest {

    private String title;

    private String description;

    private String altText;

    private Boolean isFeatured;

    private Integer sortOrder;

    private Set<Long> categoryIds;
}
