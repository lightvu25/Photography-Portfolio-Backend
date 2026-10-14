package com.photography.backend.gallery.service;

import com.photography.backend.cloudinary.CloudinaryService;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.dto.PhotoReorderItem;
import com.photography.backend.gallery.dto.PhotoUpdateRequest;
import com.photography.backend.gallery.entity.Category;
import com.photography.backend.gallery.entity.CategoryType;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import com.photography.backend.gallery.repository.CategoryRepository;
import com.photography.backend.gallery.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

    public static final String PHOTO_FOLDER = "haibau/photos";

    private final PhotoRepository photoRepository;
    private final CategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<Photo> getPublishedPhotos() {
        return stripNonPortfolioCategories(photoRepository.findPublishedWithCategories());
    }

    @Transactional(readOnly = true)
    public List<Photo> getFeaturedPhotos() {
        return stripNonPortfolioCategories(photoRepository.findFeaturedWithCategories());
    }

    /**
     * Removes non-PORTFOLIO categories from the serialised photo payload so a
     * photo that is public via a PORTFOLIO category does not leak the names or
     * ids of its internal (PACKAGE-type) categories. Safe to mutate the loaded
     * entities: readOnly transactions use FlushMode.MANUAL, so nothing is
     * written back.
     */
    private List<Photo> stripNonPortfolioCategories(List<Photo> photos) {
        for (Photo photo : photos) {
            photo.getCategories().removeIf(c -> c.getType() != CategoryType.PORTFOLIO);
        }
        return photos;
    }

    @Transactional(readOnly = true)
    public List<Photo> getAllPhotos() {
        return photoRepository.findAllWithCategories();
    }

    @Transactional(readOnly = true)
    public Photo getPhoto(Long id) {
        return photoRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Photo not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Photo uploadPhoto(MultipartFile file, String title, String description,
                             String altText, Integer sortOrder, Set<Long> categoryIds) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Image file is required", "FILE_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> uploadResult;
        try {
            uploadResult = cloudinaryService.uploadFile(file, PHOTO_FOLDER);
        } catch (IOException e) {
            log.error("Cloudinary upload failed", e);
            throw new BusinessException("Image upload failed", "UPLOAD_FAILED", HttpStatus.BAD_GATEWAY);
        }

        String imageUrl = (String) uploadResult.get("secure_url");
        String publicId = (String) uploadResult.get("public_id");
        if (imageUrl == null || publicId == null) {
            throw new BusinessException("Image upload failed", "UPLOAD_FAILED", HttpStatus.BAD_GATEWAY);
        }

        Photo photo = new Photo();
        photo.setTitle(title);
        photo.setDescription(description);
        photo.setAltText(altText);
        photo.setSortOrder(sortOrder != null ? sortOrder : 0);
        photo.setCloudinaryPublicId(publicId);
        photo.setImageUrl(imageUrl);
        photo.setThumbnailUrl(deriveThumbnailUrl(imageUrl));
        photo.setStatus(PhotoStatus.DRAFT);
        photo.setCategories(resolveCategories(categoryIds));

        return photoRepository.save(photo);
    }

    /** Atomic batch reorder — one transaction updates every affected sortOrder. */
    @Transactional
    public List<Photo> reorder(List<PhotoReorderItem> items) {
        Map<Long, Integer> orders = new HashMap<>();
        for (PhotoReorderItem item : items) orders.put(item.getId(), item.getSortOrder());

        List<Photo> photos = photoRepository.findAllById(orders.keySet());
        if (photos.size() != orders.size()) {
            throw new BusinessException("Một hoặc nhiều ảnh không tồn tại", "NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        for (Photo photo : photos) photo.setSortOrder(orders.get(photo.getId()));
        photoRepository.saveAll(photos);
        return photoRepository.findAllWithCategories();
    }

    @Transactional
    public Photo updatePhoto(Long id, PhotoUpdateRequest request) {
        Photo photo = getPhoto(id);

        if (request.getTitle() != null) {
            photo.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            photo.setDescription(request.getDescription());
        }
        if (request.getAltText() != null) {
            photo.setAltText(request.getAltText());
        }
        if (request.getIsFeatured() != null) {
            photo.setIsFeatured(request.getIsFeatured());
        }
        if (request.getSortOrder() != null) {
            photo.setSortOrder(request.getSortOrder());
        }
        if (request.getCategoryIds() != null) {
            photo.setCategories(resolveCategories(request.getCategoryIds()));
        }

        return photoRepository.save(photo);
    }

    @Transactional
    public Photo publish(Long id) {
        Photo photo = getPhoto(id);
        photo.setStatus(PhotoStatus.PUBLISHED);
        return photoRepository.save(photo);
    }

    @Transactional
    public Photo unpublish(Long id) {
        Photo photo = getPhoto(id);
        photo.setStatus(PhotoStatus.DRAFT);
        return photoRepository.save(photo);
    }

    /**
     * Hard delete. The Cloudinary asset is destroyed first: if that fails the
     * database row is kept so the public_id is never lost and the admin can
     * retry. "Not found" in Cloudinary counts as success - the asset is already
     * gone. photo_categories rows are removed by the FK cascade.
     */
    @Transactional
    public void deletePhoto(Long id) {
        Photo photo = getPhoto(id);
        cloudinaryService.deleteFile(photo.getCloudinaryPublicId());
        photoRepository.delete(photo);
    }

    private Set<Category> resolveCategories(Set<Long> categoryIds) {
        Set<Category> categories = new HashSet<>();
        if (categoryIds == null) {
            return categories;
        }
        for (Long categoryId : categoryIds) {
            categories.add(categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BusinessException("Category not found: " + categoryId,
                            "CATEGORY_NOT_FOUND", HttpStatus.BAD_REQUEST)));
        }
        return categories;
    }

    private String deriveThumbnailUrl(String secureUrl) {
        int marker = secureUrl.indexOf("/upload/");
        if (marker < 0) {
            return secureUrl;
        }
        return secureUrl.substring(0, marker) + "/upload/w_400,c_fill,q_auto/"
                + secureUrl.substring(marker + "/upload/".length());
    }
}
