package com.photography.backend.auth.security;

import com.photography.backend.auth.entity.AdminUser;
import com.photography.backend.auth.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Test
    void adminUserIsSeededWithBcryptHash() {
        AdminUser admin = adminUserRepository.findByEmail("admin@haibau.com").orElseThrow();
        assertThat(admin.getPassword()).startsWith("$2");
        assertThat(admin.getPassword()).isNotEqualTo("password");
    }

    @Test
    void validAdminTokenGrantsAccess() throws Exception {
        UserDetails user = userDetailsService.loadUserByUsername("admin@haibau.com");
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void invalidTokenRejected() throws Exception {
        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenRejected() throws Exception {
        UserDetails user = userDetailsService.loadUserByUsername("admin@haibau.com");
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
