package com.photography.backend.site.controller;
import com.photography.backend.site.entity.SiteSettings;
import com.photography.backend.site.repository.SiteSettingsRepository;
import com.photography.backend.site.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/site") @RequiredArgsConstructor
public class SiteController {
    private final SiteSettingsRepository repository;
    private final SiteService siteService;
    @GetMapping
    public ResponseEntity<SiteSettings> getSettings() {
        return repository.findAll().stream().findFirst()
            .map(s -> ResponseEntity.ok(siteService.withResolvedAboutPhoto(s)))
            .orElse(ResponseEntity.notFound().build());
    }
}