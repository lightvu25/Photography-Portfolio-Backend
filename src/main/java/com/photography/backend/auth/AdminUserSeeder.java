package com.photography.backend.auth;

import com.photography.backend.auth.entity.AdminUser;
import com.photography.backend.auth.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserSeeder implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.initial-password}")
    private String adminInitialPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminInitialPassword));
        adminUserRepository.save(admin);
        log.info("Seeded admin user: {}", adminEmail);
    }
}
