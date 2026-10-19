package com.photography.backend.site.service;

import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import com.photography.backend.gallery.repository.PhotoRepository;
import com.photography.backend.gallery.service.CategoryService;
import com.photography.backend.site.entity.SiteSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final PhotoRepository photoRepository;

    /**
     * Resolves the photo shown in the homepage About section:
     * the explicit selection when it is still eligible (published AND in the
     * reserved Admin category), else the first published Admin-category photo,
     * else null — never a photo from a public category.
     */
    @Transactional(readOnly = true)
    public SiteSettings withResolvedAboutPhoto(SiteSettings settings) {
        if (settings == null) {
            return null;
        }
        Photo selected = settings.getProfilePhoto();
        if (selected != null && isEligible(selected)) {
            settings.setAboutPhoto(selected);
            return settings;
        }
        settings.setAboutPhoto(
                photoRepository.findPublishedByCategorySlug(CategoryService.ADMIN_CATEGORY_SLUG)
                        .stream().findFirst().orElse(null));
        return settings;
    }

    private boolean isEligible(Photo photo) {
        return photo.getStatus() == PhotoStatus.PUBLISHED
                && photo.getCategories().stream()
                        .anyMatch(c -> CategoryService.ADMIN_CATEGORY_SLUG.equals(c.getSlug()));
    }
}
