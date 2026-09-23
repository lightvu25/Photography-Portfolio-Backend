package com.photography.backend.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackRequest {

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Feedback content is required")
    private String content;
}
