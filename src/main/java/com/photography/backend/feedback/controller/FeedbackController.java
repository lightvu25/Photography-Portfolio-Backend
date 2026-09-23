package com.photography.backend.feedback.controller;

import com.photography.backend.feedback.dto.FeedbackRequest;
import com.photography.backend.feedback.entity.Feedback;
import com.photography.backend.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    public ResponseEntity<List<Feedback>> getPublishedFeedback() {
        return ResponseEntity.ok(feedbackService.getPublishedFeedback());
    }

    @PostMapping
    public ResponseEntity<Feedback> createFeedback(@Valid @RequestBody FeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.createFeedback(request));
    }
}
