package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void deleteFile(String publicId) {
        try {
            log.info("Deleting file from Cloudinary: {}", publicId);
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.error("Failed to delete file from Cloudinary: {}", publicId, e);
            // We log the error but don't throw to prevent blocking the DB deletion
        }
    }
}