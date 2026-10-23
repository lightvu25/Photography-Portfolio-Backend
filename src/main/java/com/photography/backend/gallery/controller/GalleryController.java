package com.photography.backend.gallery.controller;

import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.service.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    @GetMapping
    public ResponseEntity<List<Photo>> getPublishedPhotos() {
        return ResponseEntity.ok(galleryService.getPublishedPhotos());
    }

    /** Published customer-feedback screenshots (Feedback category only). */
    @GetMapping("/feedback")
    public ResponseEntity<List<Photo>> getFeedbackPhotos() {
        return ResponseEntity.ok(galleryService.getPublishedFeedbackPhotos());
    }

    @GetMapping("/featured")
    public ResponseEntity<List<Photo>> getFeaturedPhotos() {
        return ResponseEntity.ok(galleryService.getFeaturedPhotos());
    }
}
