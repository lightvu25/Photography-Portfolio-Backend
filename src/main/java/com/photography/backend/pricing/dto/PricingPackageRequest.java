package com.photography.backend.pricing.dto;

import com.photography.backend.booking.entity.SessionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class PricingPackageRequest {

    @NotBlank(message = "Package name is required")
    private String name;

    @NotNull(message = "Group size is required")
    @Min(value = 1, message = "Group size must be at least 1")
    private Integer groupSize;

    @NotNull(message = "Session type is required")
    private SessionType sessionType;

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price must not be negative")
    private Long price;

    private String description;

    private Integer sortOrder;

    private Boolean isActive;
}
