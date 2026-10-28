package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.dto.PhotoCompleteUploadRequest;
import com.photography.backend.gallery.dto.PhotoUploadSignatureResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CloudinaryService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/avif"
    );

    private static final Set<String> ALLOWED_FORMATS = Set.of(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "avif"
    );

    private static final Pattern SAFE_LEAF_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,255}$");

    private final Cloudinary cloudinary;
    private final ImageResizingService imageResizingService;
    private final Semaphore uploadSemaphore;
    private final long acquireTimeoutMs;

    @Autowired
    public CloudinaryService(
            Cloudinary cloudinary,
            ImageResizingService imageResizingService,
            @Value("${app.upload.max-concurrent:2}") int maxConcurrentUploads,
            @Value("${app.upload.acquire-timeout-ms:15000}") long acquireTimeoutMs
    ) {
        this.cloudinary = cloudinary;
        this.imageResizingService = imageResizingService != null ? imageResizingService : new ImageResizingService();
        this.uploadSemaphore = new Semaphore(Math.max(1, maxConcurrentUploads), true);
        this.acquireTimeoutMs = Math.max(0L, acquireTimeoutMs);
    }

    public CloudinaryService(
            Cloudinary cloudinary,
            int maxConcurrentUploads,
            long acquireTimeoutMs
    ) {
        this(cloudinary, new ImageResizingService(), maxConcurrentUploads, acquireTimeoutMs);
    }

    public CloudinaryService(Cloudinary cloudinary) {
        this(cloudinary, new ImageResizingService(), 2, 15_000L);
    }

    /**
     * Generates a short-lived signed upload ticket for direct browser-to-Cloudinary upload.
     * Signs ONLY server-controlled parameters ({@code folder}, server-generated {@code public_id},
     * and {@code timestamp}). Never exposes {@code apiSecret}.
     */
    public PhotoUploadSignatureResponse createSignedUploadTicket(
            String folder,
            String contentType,
            Long fileSizeBytes
    ) {
        if (folder == null || folder.isBlank()) {
            throw new BusinessException("Upload folder is required", "INVALID_UPLOAD_FOLDER", HttpStatus.BAD_REQUEST);
        }
        if (contentType != null && !contentType.isBlank()) {
            String normalizedMime = contentType.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(normalizedMime)) {
                throw new BusinessException(
                        "Định dạng file không được hỗ trợ — chỉ chấp nhận JPEG, PNG, WebP hoặc AVIF",
                        "UNSUPPORTED_IMAGE_FORMAT",
                        HttpStatus.BAD_REQUEST);
            }
        }
        if (fileSizeBytes != null && fileSizeBytes <= 0L) {
            throw new BusinessException(
                    "Dung lượng file không hợp lệ",
                    "INVALID_FILE_SIZE",
                    HttpStatus.BAD_REQUEST);
        }

        String cloudName = cloudinary.config != null ? cloudinary.config.cloudName : null;
        String apiKey = cloudinary.config != null ? cloudinary.config.apiKey : null;
        String apiSecret = cloudinary.config != null ? cloudinary.config.apiSecret : null;
        if (cloudName == null || cloudName.isBlank()
                || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            throw new BusinessException(
                    "Cấu hình Cloudinary chưa sẵn sàng trên máy chủ",
                    "CLOUDINARY_MISCONFIGURED",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        long timestamp = Instant.now().getEpochSecond();
        String leafPublicId = UUID.randomUUID().toString();

        Map<String, Object> paramsToSign = new HashMap<>();
        paramsToSign.put("folder", folder);
        paramsToSign.put("public_id", leafPublicId);
        paramsToSign.put("timestamp", timestamp);

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret);

        return PhotoUploadSignatureResponse.builder()
                .signature(signature)
                .timestamp(timestamp)
                .apiKey(apiKey)
                .cloudName(cloudName)
                .folder(folder)
                .publicId(leafPublicId)
                .uploadUrl("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload")
                .build();
    }

    /**
     * Verifies a completed direct upload before persisting metadata to PostgreSQL:
     * 1. Enforces that {@code publicId} resides strictly inside {@code expectedFolder}.
     * 2. Validates resource_type, format, dimensions, and byte size.
     * 3. Verifies Cloudinary's cryptographic response signature via {@link Cloudinary#verifyApiResponseSignature}.
     * 4. Reconstructs the canonical HTTPS delivery URL from the server's own {@code cloudName}
     *    so client-supplied URLs are never blindly trusted.
     */
    public String verifyAndBuildSecureUrl(String expectedFolder, PhotoCompleteUploadRequest request) {
        if (request == null) {
            throw new BusinessException("Dữ liệu xác nhận tải ảnh không hợp lệ", "INVALID_UPLOAD_DATA", HttpStatus.BAD_REQUEST);
        }

        String rawPublicId = request.getPublicId() != null ? request.getPublicId().trim() : "";
        String requiredPrefix = expectedFolder + "/";
        if (!rawPublicId.startsWith(requiredPrefix)
                || rawPublicId.contains("..")
                || rawPublicId.contains("//")
                || rawPublicId.contains("\\")) {
            throw new BusinessException(
                    "Định danh ảnh (public_id) không thuộc thư mục cho phép",
                    "INVALID_PUBLIC_ID",
                    HttpStatus.BAD_REQUEST);
        }

        String leafId = rawPublicId.substring(requiredPrefix.length());
        if (!SAFE_LEAF_ID_PATTERN.matcher(leafId).matches()) {
            throw new BusinessException(
                    "Định danh ảnh (public_id) không hợp lệ",
                    "INVALID_PUBLIC_ID",
                    HttpStatus.BAD_REQUEST);
        }

        if (request.getResourceType() != null && !request.getResourceType().isBlank()
                && !"image".equalsIgnoreCase(request.getResourceType().trim())) {
            throw new BusinessException(
                    "Loại tài nguyên không hợp lệ — chỉ chấp nhận image",
                    "INVALID_RESOURCE_TYPE",
                    HttpStatus.BAD_REQUEST);
        }

        String rawFormat = request.getFormat() != null ? request.getFormat().trim().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_FORMATS.contains(rawFormat)) {
            throw new BusinessException(
                    "Định dạng ảnh không được hỗ trợ: " + rawFormat,
                    "UNSUPPORTED_IMAGE_FORMAT",
                    HttpStatus.BAD_REQUEST);
        }
        String canonicalFormat = "jpeg".equals(rawFormat) ? "jpg" : rawFormat;

        if (request.getVersion() == null || request.getVersion() <= 0L
                || request.getBytes() == null || request.getBytes() <= 0L
                || request.getWidth() == null || request.getWidth() <= 0
                || request.getHeight() == null || request.getHeight() <= 0) {
            throw new BusinessException(
                    "Thông số kích thước hoặc phiên bản ảnh không hợp lệ",
                    "INVALID_IMAGE_METADATA",
                    HttpStatus.BAD_REQUEST);
        }

        String responseSignature = request.getSignature() != null ? request.getSignature().trim() : "";
        if (responseSignature.isEmpty()) {
            throw new BusinessException(
                    "Thiếu chữ ký xác thực từ Cloudinary",
                    "INVALID_UPLOAD_SIGNATURE",
                    HttpStatus.BAD_REQUEST);
        }

        boolean signatureValid = cloudinary.verifyApiResponseSignature(
                rawPublicId,
                String.valueOf(request.getVersion()),
                responseSignature
        );
        if (!signatureValid) {
            log.warn("Rejected photo upload completion with invalid Cloudinary signature for publicId={}", rawPublicId);
            throw new BusinessException(
                    "Chữ ký xác thực từ Cloudinary không hợp lệ",
                    "INVALID_UPLOAD_SIGNATURE",
                    HttpStatus.BAD_REQUEST);
        }

        String cloudName = cloudinary.config != null ? cloudinary.config.cloudName : null;
        if (cloudName == null || cloudName.isBlank()) {
            throw new BusinessException(
                    "Cấu hình Cloudinary chưa sẵn sàng trên máy chủ",
                    "CLOUDINARY_MISCONFIGURED",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String expectedBasePrefix = "https://res.cloudinary.com/" + cloudName + "/image/upload/";
        if (request.getSecureUrl() != null && !request.getSecureUrl().isBlank()) {
            String clientUrl = request.getSecureUrl().trim();
            String expectedSuffixJpg = "/" + rawPublicId + "." + canonicalFormat;
            String expectedSuffixRaw = "/" + rawPublicId + "." + rawFormat;
            if (!clientUrl.startsWith(expectedBasePrefix)
                    || (!clientUrl.endsWith(expectedSuffixJpg) && !clientUrl.endsWith(expectedSuffixRaw))) {
                throw new BusinessException(
                        "URL ảnh không thuộc môi trường Cloudinary hợp lệ",
                        "INVALID_UPLOAD_URL",
                        HttpStatus.BAD_REQUEST);
            }
        }

        return expectedBasePrefix + "v" + request.getVersion() + "/" + rawPublicId + "." + canonicalFormat;
    }

    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
        boolean acquired;
        try {
            acquired = uploadSemaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    "Quá trình tải ảnh bị gián đoạn — vui lòng thử lại",
                    "UPLOAD_INTERRUPTED",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        if (!acquired) {
            log.warn("Upload concurrency limit reached; rejecting concurrent upload request for folder {}", folder);
            throw new BusinessException(
                    "Hệ thống đang xử lý ảnh khác, vui lòng thử lại sau giây lát",
                    "UPLOAD_BUSY",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        try (ImageResizingService.ProcessedImage processed = imageResizingService.process(file)) {
            byte[] bytes = processed.readBytes();
            log.info("Uploading {} image ({}x{}, resized={}) to Cloudinary, folder: {}, size: {} bytes",
                    processed.getFormat(), processed.getWidth(), processed.getHeight(),
                    processed.isResized(), folder, bytes.length);
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto"
            );
            try {
                return cloudinary.uploader().upload(bytes, options);
            } catch (RuntimeException e) {
                // Preserve the real Cloudinary rejection (permissions, size, etc.)
                // instead of letting it surface as a generic 500.
                log.error("Cloudinary rejected upload: {}", e.getMessage());
                throw new BusinessException("Cloudinary upload failed: " + e.getMessage(),
                        "UPLOAD_FAILED", HttpStatus.BAD_GATEWAY);
            }
        } finally {
            uploadSemaphore.release();
        }
    }

    /**
     * Deletes a Cloudinary asset. Returns normally only when the asset is gone:
     * a "not found" result is treated as success since the desired end state
     * (asset no longer exists) is already reached. Any other failure throws so
     * callers never silently lose track of a live asset.
     */
    public void deleteFile(String publicId) {
        try {
            log.info("Deleting file from Cloudinary: {}", publicId);
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            Object status = result.get("result");
            if (!"ok".equals(status) && !"not found".equals(status)) {
                throw new BusinessException("Cloudinary deletion failed: " + status,
                        "CLOUDINARY_DELETE_FAILED", HttpStatus.BAD_GATEWAY);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete file from Cloudinary: {}", publicId, e);
            throw new BusinessException("Cloudinary deletion failed",
                    "CLOUDINARY_DELETE_FAILED", HttpStatus.BAD_GATEWAY);
        }
    }
}
