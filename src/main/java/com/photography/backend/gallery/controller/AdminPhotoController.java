package com.photography.backend.gallery.controller;

import com.photography.backend.gallery.dto.BulkCategoryRequest;
import com.photography.backend.gallery.dto.PhotoCompleteUploadRequest;
import com.photography.backend.gallery.dto.PhotoReorderItem;
import com.photography.backend.gallery.dto.PhotoUpdateRequest;
import com.photography.backend.gallery.dto.PhotoUploadSignatureRequest;
import com.photography.backend.gallery.dto.PhotoUploadSignatureResponse;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.service.GalleryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/photos")
@RequiredArgsConstructor
public class AdminPhotoController {

    private final GalleryService galleryService;

    @GetMapping
    public ResponseEntity<List<Photo>> getAllPhotos() {
        return ResponseEntity.ok(galleryService.getAllPhotos());
    }

    /**
     * Phase A of Signed Direct Upload: generates a signed upload ticket for
     * direct browser-to-Cloudinary upload.
     */
    @PostMapping("/upload-signature")
    public ResponseEntity<PhotoUploadSignatureResponse> generateUploadSignature(
            @RequestBody(required = false) PhotoUploadSignatureRequest request) {
        return ResponseEntity.ok(galleryService.generateUploadSignature(request));
    }

    /**
     * Phase C of Signed Direct Upload: verifies the completed Cloudinary asset
     * and persists photo metadata + Category links in PostgreSQL.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Photo> completePhotoUpload(
            @Valid @RequestBody PhotoCompleteUploadRequest request) {
        Photo photo = galleryService.completePhotoUpload(request);
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
