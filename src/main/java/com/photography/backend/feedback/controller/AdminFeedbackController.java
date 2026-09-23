package com.photography.backend.feedback.controller;

import com.photography.backend.feedback.dto.FeedbackUpdateRequest;
import com.photography.backend.feedback.entity.Feedback;
import com.photography.backend.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/feedbacks")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    public ResponseEntity<List<Feedback>> getAllFeedback() {
        return ResponseEntity.ok(feedbackService.getAllFeedback());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Feedback> updateFeedback(@PathVariable Long id,
                                                   @Valid @RequestBody FeedbackUpdateRequest request) {
        return ResponseEntity.ok(feedbackService.updateFeedback(id, request));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Feedback> attachImage(@PathVariable Long id,
                                                @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(feedbackService.attachImage(id, file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeedback(@PathVariable Long id) {
        feedbackService.deleteFeedback(id);
        return ResponseEntity.noContent().build();
    }
}
