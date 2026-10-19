package com.photography.backend.gallery.controller;

import com.photography.backend.gallery.dto.BulkCategoryRequest;
import com.photography.backend.gallery.dto.PhotoReorderItem;
import com.photography.backend.gallery.dto.PhotoUpdateRequest;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.service.GalleryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/photos")
@RequiredArgsConstructor
public class AdminPhotoController {

    private final GalleryService galleryService;

    @GetMapping
    public ResponseEntity<List<Photo>> getAllPhotos() {
        return ResponseEntity.ok(galleryService.getAllPhotos());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Photo> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) Set<Long> categoryIds,
            @RequestParam(required = false) Double cropX,
            @RequestParam(required = false) Double cropY,
            @RequestParam(required = false) Double cropW,
            @RequestParam(required = false) Double cropH) {
        Photo photo = galleryService.uploadPhoto(file, title, description, altText, sortOrder,
                categoryIds, cropX, cropY, cropW, cropH);
        return ResponseEntity.status(HttpStatus.CREATED).body(photo);
    }

    @PutMapping("/reorder")
    public ResponseEntity<List<Photo>> reorderPhotos(@Valid @RequestBody List<PhotoReorderItem> items) {
        return ResponseEntity.ok(galleryService.reorder(items));
    }

    /** Atomic bulk add/remove of categories across the selected photos. */
    @PutMapping("/bulk-categories")
    public ResponseEntity<List<Photo>> bulkUpdateCategories(@Valid @RequestBody BulkCategoryRequest request) {
        return ResponseEntity.ok(galleryService.updatePhotoCategories(
                request.getPhotoIds(), request.getAddCategoryIds(), request.getRemoveCategoryIds()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Photo> updatePhoto(@PathVariable Long id,
                                             @Valid @RequestBody PhotoUpdateRequest request) {
        return ResponseEntity.ok(galleryService.updatePhoto(id, request));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Photo> publishPhoto(@PathVariable Long id) {
        return ResponseEntity.ok(galleryService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<Photo> unpublishPhoto(@PathVariable Long id) {
        return ResponseEntity.ok(galleryService.unpublish(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id) {
        galleryService.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }
}
