package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.photography.backend.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final Semaphore uploadSemaphore;
    private final long acquireTimeoutMs;

    @Autowired
    public CloudinaryService(
            Cloudinary cloudinary,
            @Value("${app.upload.max-concurrent:2}") int maxConcurrentUploads,
            @Value("${app.upload.acquire-timeout-ms:15000}") long acquireTimeoutMs
    ) {
        this.cloudinary = cloudinary;
        this.uploadSemaphore = new Semaphore(Math.max(1, maxConcurrentUploads), true);
        this.acquireTimeoutMs = Math.max(0L, acquireTimeoutMs);
    }

    public CloudinaryService(Cloudinary cloudinary) {
        this(cloudinary, 2, 15_000L);
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

        try {
            byte[] bytes = file.getBytes();
            log.info("Uploading file to Cloudinary, folder: {}, size: {} bytes", folder, bytes.length);
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto"
            );
            try {
                return cloudinary.uploader().upload(bytes, options);
            } catch (RuntimeException e) {
                // Preserve the real Cloudinary rejection (permissions, etc.)
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
