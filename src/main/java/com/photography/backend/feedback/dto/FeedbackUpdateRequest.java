package com.photography.backend.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackUpdateRequest {

    @NotBlank(message = "Customer name is required")
    private String customerName;

    private String content;

    private Integer sortOrder;

    private Boolean isPublished;
}
