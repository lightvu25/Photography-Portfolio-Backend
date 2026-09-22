package com.photography.backend.site.repository;
import com.photography.backend.site.entity.SiteSettings;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Long> {
}