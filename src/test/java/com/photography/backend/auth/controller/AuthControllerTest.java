package com.photography.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photography.backend.auth.dto.LoginRequest;
import com.photography.backend.auth.entity.AdminUser;
import com.photography.backend.auth.repository.AdminUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@AutoConfigureMockMvc

@ActiveProfiles("test")
@Transactional
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adminUserRepository.deleteAllInBatch();
        AdminUser user = new AdminUser();
        user.setEmail("admin@haibau.com");
        user.setPassword(passwordEncoder.encode("password"));
        adminUserRepository.save(user);
    }

    @Test
    void correctLoginSucceeds() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@haibau.com");
        req.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordFails() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@haibau.com");
        req.setPassword("wrong");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized()); // Or 403 based on Spring Security setup
    }

    @Test
    void publicEndpointsAccessible() throws Exception {
        mockMvc.perform(get("/api/pricing"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isUnauthorized()); // 403 Forbidden without token
    }
}
