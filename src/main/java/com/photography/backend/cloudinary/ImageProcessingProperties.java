package com.photography.backend.cloudinary;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for server-side image resizing prior to Cloudinary upload.
 *
 * <p>Can be overridden via {@code application.yml} or environment variables:
 * <ul>
 *   <li>{@code image.processing.max-long-edge} / {@code IMAGE_MAX_LONG_EDGE} (default: {@code 2560})</li>
 *   <li>{@code image.processing.max-dimension} / {@code IMAGE_MAX_DIMENSION} (default: {@code 20000})</li>
 *   <li>{@code image.processing.max-pixels} / {@code IMAGE_MAX_PIXELS} (default: {@code 100000000})</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "image.processing")
@Getter
public class ImageProcessingProperties {

    public static final int DEFAULT_MAX_LONG_EDGE = 2560;
    public static final int DEFAULT_MAX_DIMENSION = 20_000;
    public static final long DEFAULT_MAX_PIXELS = 100_000_000L;

    /** Maximum allowed length (in pixels) for the longest edge of an uploaded image. */
    private int maxLongEdge = DEFAULT_MAX_LONG_EDGE;

    /** Maximum allowed single-axis dimension (width or height) before decoding is rejected. */
    private int maxDimension = DEFAULT_MAX_DIMENSION;

    /** Maximum allowed total pixel count (width * height) before decoding is rejected. */
    private long maxPixels = DEFAULT_MAX_PIXELS;

    public ImageProcessingProperties() {
    }

    public ImageProcessingProperties(int maxLongEdge, int maxDimension, long maxPixels) {
        this.maxLongEdge = maxLongEdge;
        this.maxDimension = maxDimension;
        this.maxPixels = maxPixels;
        validate();
    }

    public void setMaxLongEdge(int maxLongEdge) {
        this.maxLongEdge = maxLongEdge;
    }

    public void setMaxDimension(int maxDimension) {
        this.maxDimension = maxDimension;
    }

    public void setMaxPixels(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    @PostConstruct
    public void validate() {
        if (maxLongEdge <= 0) {
            throw new IllegalArgumentException(
                    "image.processing.max-long-edge must be a positive integer, got: " + maxLongEdge);
        }
        if (maxDimension <= 0) {
            throw new IllegalArgumentException(
                    "image.processing.max-dimension must be a positive integer, got: " + maxDimension);
        }
        if (maxLongEdge > maxDimension) {
            throw new IllegalArgumentException(
                    "image.processing.max-long-edge (" + maxLongEdge
                            + ") cannot exceed image.processing.max-dimension (" + maxDimension + ")");
        }
        if (maxPixels <= 0) {
            throw new IllegalArgumentException(
                    "image.processing.max-pixels must be a positive number, got: " + maxPixels);
        }
    }
}
