package com.photography.backend.auth.security;

import com.photography.backend.auth.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return adminUserRepository.findByEmail(username)
                .map(user -> User.builder()
                        .username(user.getEmail())
                        .password(user.getPassword())
                        .roles("ADMIN")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}