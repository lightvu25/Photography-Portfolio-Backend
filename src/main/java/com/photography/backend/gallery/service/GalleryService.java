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

    /** Customer-feedback screenshots (reserved Feedback category), published only. */
    @Transactional(readOnly = true)
    public List<Photo> getPublishedFeedbackPhotos() {
        List<Photo> photos = photoRepository.findPublishedFeedbackPhotos();
        for (Photo photo : photos) {
            photo.getCategories().clear();
        }
        return photos;
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
        return uploadPhoto(file, title, description, altText, sortOrder, categoryIds,
                null, null, null, null);
    }

    @Transactional
    public Photo uploadPhoto(MultipartFile file, String title, String description,
                             String altText, Integer sortOrder, Set<Long> categoryIds,
                             Double cropX, Double cropY, Double cropW, Double cropH) {
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
        if (cropX != null && cropY != null && cropW != null && cropH != null) {
            applyCrop(photo, cropX, cropY, cropW, cropH);
        }

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
            Set<Category> newCats = resolveCategories(request.getCategoryIds());
            photo.getCategories().clear();
            photo.getCategories().addAll(newCats);
        }
        if (Boolean.TRUE.equals(request.getClearCrop())) {
            clearCrop(photo);
        } else if (request.getCropX() != null && request.getCropY() != null
                && request.getCropW() != null && request.getCropH() != null) {
            applyCrop(photo, request.getCropX(), request.getCropY(),
                    request.getCropW(), request.getCropH());
        }

        return photoRepository.save(photo);
    }

    /**
     * Atomic bulk category edit — one transaction updates every selected
     * photo, so a failed validation leaves nothing half-applied.
     *
     * Rules:
     * - adding the reserved Admin category makes it the photo's ONLY category
     *   (self-introduction is reserved);
     * - any result that would mix Admin with other categories rejects the
     *   whole request — bulk operations cannot bypass the reserved rule.
     */
    @Transactional
    public List<Photo> updatePhotoCategories(Set<Long> photoIds,
                                             Set<Long> addCategoryIds,
                                             Set<Long> removeCategoryIds) {
        List<Photo> photos = photoRepository.findAllByIdInWithCategories(photoIds);
        if (photos.size() != photoIds.size()) {
            throw new BusinessException("Một hoặc nhiều ảnh không tồn tại", "NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        Set<Category> toAdd = resolveCategories(addCategoryIds);
        // Removals only need the categories to exist — removing Admin +
        // others can never create a mixed state, so the exclusivity check
        // applies to the add-set and the per-photo result, not the remove-set.
        Set<Category> toRemove = resolveExisting(removeCategoryIds);
        Category reserved = toAdd.stream()
                .filter(c -> CategoryService.ADMIN_CATEGORY_SLUG.equals(c.getSlug()))
                .findFirst().orElse(null);

        // Validate every result BEFORE mutating — a rule violation must leave
        // all photos untouched, not just the ones not yet processed.
        List<Set<Category>> results = photos.stream().map(photo -> {
            if (reserved != null) {
                // Admin applied -> Admin is the ONLY category on each photo.
                return Set.of(reserved);
            }
            Set<Category> next = new HashSet<>(photo.getCategories());
            next.removeAll(toRemove);
            next.addAll(toAdd);
            assertCategorySetAllowed(next);
            return next;
        }).toList();
        for (int i = 0; i < photos.size(); i++) {
            Photo p = photos.get(i);
            p.getCategories().clear();
            p.getCategories().addAll(results.get(i));
        }
        photoRepository.saveAll(photos);
        return photoRepository.findAllWithCategories();
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
        assertCategorySetAllowed(categories);
        return categories;
    }

    /** Same lookup as {@link #resolveCategories} without the exclusivity rule. */
    private Set<Category> resolveExisting(Set<Long> categoryIds) {
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

    /**
     * The reserved Admin category is exclusive — a photo carrying it may hold
     * no other category. Rejected outright so mixed states can never persist.
     */
    private void assertCategorySetAllowed(Set<Category> categories) {
        boolean hasReserved = categories.stream()
                .anyMatch(c -> CategoryService.ADMIN_CATEGORY_SLUG.equals(c.getSlug()));
        if (hasReserved && categories.size() > 1) {
            throw new BusinessException(
                    "Danh mục Admin chỉ dành cho ảnh giới thiệu — không thể gán cùng danh mục khác",
                    "RESERVED_CATEGORY", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Bakes a c_crop transformation into the display + thumbnail URLs while
     * keeping the pristine upload URL for re-editing. Cloudinary accepts
     * decimal fractions (w_0.5 = 50% of the stored image), so the saved crop
     * stays correct regardless of the resolution actually uploaded.
     *
     * Small sub-pixel overshoots from browser DOM rounding (e.g. -0.001 or
     * 1.002 from react-easy-crop) are clamped to [0, 1] rather than rejected.
     */
    private void applyCrop(Photo photo, double x, double y, double w, double h) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h)
                || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(w) || Double.isInfinite(h)
                || x < -0.05 || y < -0.05 || w <= 0 || h <= 0
                || x >= 1 || y >= 1 || w > 1.05 || h > 1.05
                || x + w > 1.05 || y + h > 1.05) {
            throw new BusinessException("Vùng cắt không hợp lệ", "INVALID_CROP", HttpStatus.BAD_REQUEST);
        }
        double clampedX = Math.max(0.0, Math.min(x, 0.9999));
        double clampedY = Math.max(0.0, Math.min(y, 0.9999));
        double clampedW = Math.max(0.0001, Math.min(w, 1.0 - clampedX));
        double clampedH = Math.max(0.0001, Math.min(h, 1.0 - clampedY));

        String original = photo.getOriginalImageUrl() != null
                ? photo.getOriginalImageUrl() : photo.getImageUrl();
        String transform = "c_crop,x_" + fmt(clampedX) + ",y_" + fmt(clampedY)
                + ",w_" + fmtDim(clampedW) + ",h_" + fmtDim(clampedH);
        photo.setOriginalImageUrl(original);
        photo.setImageUrl(insertTransform(original, transform));
        photo.setThumbnailUrl(insertTransform(original, transform + "/w_600,c_limit,q_auto"));
        photo.setCropX(clampedX);
        photo.setCropY(clampedY);
        photo.setCropW(clampedW);
        photo.setCropH(clampedH);
    }

    /** Restores the untouched upload URL and drops the saved crop metadata. */
    private void clearCrop(Photo photo) {
        String original = photo.getOriginalImageUrl() != null
                ? photo.getOriginalImageUrl() : photo.getImageUrl();
        photo.setImageUrl(original);
        photo.setThumbnailUrl(deriveThumbnailUrl(original));
        photo.setOriginalImageUrl(null);
        photo.setCropX(null);
        photo.setCropY(null);
        photo.setCropW(null);
        photo.setCropH(null);
    }

    /** Compact decimal for a 0..1 offset fraction (0, 0.5, etc.). */
    private String fmt(double v) {
        java.math.BigDecimal d = java.math.BigDecimal.valueOf(v)
                .setScale(4, java.math.RoundingMode.HALF_UP);
        return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
    }

    /**
     * Compact decimal for a 0..1 width/height fraction. When the value rounds
     * to 1, keeps "1.0" so Cloudinary treats it as 100% relative size rather
     * than 1 pixel ("w_1").
     */
    private String fmtDim(double v) {
        java.math.BigDecimal d = java.math.BigDecimal.valueOf(v)
                .setScale(4, java.math.RoundingMode.HALF_UP);
        if (d.compareTo(java.math.BigDecimal.ONE) >= 0) {
            return "1.0";
        }
        return d.signum() == 0 ? "0.0001" : d.stripTrailingZeros().toPlainString();
    }

    private String insertTransform(String secureUrl, String transformation) {
        int marker = secureUrl.indexOf("/upload/");
        if (marker < 0) {
            return secureUrl;
        }
        return secureUrl.substring(0, marker) + "/upload/" + transformation + "/"
                + secureUrl.substring(marker + "/upload/".length());
    }

    private String deriveThumbnailUrl(String secureUrl) {
        return insertTransform(secureUrl, "w_600,c_limit,q_auto");
    }
}
