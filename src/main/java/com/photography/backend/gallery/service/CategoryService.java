package com.photography.backend.gallery.service;

import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.dto.CategoryRequest;
import com.photography.backend.gallery.entity.Category;
import com.photography.backend.gallery.entity.CategoryType;
import com.photography.backend.gallery.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CategoryService {

    /**
     * Reserved slug of the internal "Admin" category (seeded V10, renamed V11).
     * It must stay PACKAGE-type and must not be deletable — removing it or
     * flipping it to PORTFOLIO would silently re-expose the photos it hides.
     * A photo in this category may hold no other category (self-intro only).
     */
    public static final String ADMIN_CATEGORY_SLUG = "admin-noi-bo";

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc();
    }

    /** Public list — portfolio categories only; package categories stay admin-side. */
    @Transactional(readOnly = true)
    public List<Category> getPortfolioCategories() {
        return categoryRepository.findAllByTypeOrderBySortOrderAsc(
                com.photography.backend.gallery.entity.CategoryType.PORTFOLIO);
    }

    @Transactional
    public Category createCategory(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setSlug(resolveSlug(request));
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        if (request.getType() != null) {
            category.setType(request.getType());
        }
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Category slug already exists", "DUPLICATE_SLUG", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public Category updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Category not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
        boolean reserved = ADMIN_CATEGORY_SLUG.equals(category.getSlug());
        category.setName(request.getName());
        category.setSlug(reserved ? ADMIN_CATEGORY_SLUG : resolveSlug(request));
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getType() != null) {
            if (reserved && request.getType() != CategoryType.PACKAGE) {
                throw new BusinessException("Danh mục Admin không thể đổi sang loại công khai",
                        "RESERVED_CATEGORY", HttpStatus.BAD_REQUEST);
            }
            category.setType(request.getType());
        }
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Category slug already exists", "DUPLICATE_SLUG", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Category not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
        if (ADMIN_CATEGORY_SLUG.equals(category.getSlug())) {
            throw new BusinessException("Danh mục Admin là danh mục hệ thống — không thể xoá",
                    "RESERVED_CATEGORY", HttpStatus.BAD_REQUEST);
        }
        // photo_categories rows are removed by the FK ON DELETE CASCADE
        categoryRepository.delete(category);
    }

    private String resolveSlug(CategoryRequest request) {
        String base = request.getSlug() != null && !request.getSlug().isBlank()
                ? request.getSlug()
                : request.getName();
        String normalized = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            throw new BusinessException("Category slug is invalid", "INVALID_SLUG", HttpStatus.BAD_REQUEST);
        }
        return slug;
    }
}
