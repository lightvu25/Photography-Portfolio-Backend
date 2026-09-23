package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.photography.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
        log.info("Uploading file to Cloudinary, folder: {}", folder);
        Map<String, Object> options = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto"
        );
        return cloudinary.uploader().upload(file.getBytes(), options);
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
