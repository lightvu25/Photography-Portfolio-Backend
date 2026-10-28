package com.photography.backend.gallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class PhotoCompleteUploadRequest {

    @NotBlank(message = "publicId is required")
    private String publicId;

    @NotNull(message = "version is required")
    @Positive(message = "version must be positive")
    private Long version;

    @NotBlank(message = "signature is required")
    private String signature;

    private String secureUrl;

    private String resourceType;

    @NotBlank(message = "format is required")
    private String format;

    @NotNull(message = "bytes is required")
    @Positive(message = "bytes must be positive")
    private Long bytes;

    @NotNull(message = "width is required")
    @Positive(message = "width must be positive")
    private Integer width;

    @NotNull(message = "height is required")
    @Positive(message = "height must be positive")
    private Integer height;

    private String title;
    private String description;
    private String altText;
    private Integer sortOrder;
    private Set<Long> categoryIds;
    private Double cropX;
    private Double cropY;
    private Double cropW;
    private Double cropH;
}
