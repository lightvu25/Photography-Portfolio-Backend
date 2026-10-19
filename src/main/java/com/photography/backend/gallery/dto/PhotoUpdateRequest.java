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

    /**
     * Crop rectangle as fractions of the stored image (0..1). Applied only when
     * all four values are present; ignored otherwise.
     */
    private Double cropX;

    private Double cropY;

    private Double cropW;

    private Double cropH;

    /** When true, removes any saved crop and restores the original URL. */
    private Boolean clearCrop;
}
