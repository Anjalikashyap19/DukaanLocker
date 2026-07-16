package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security's "username" is, by design here, the user's emailId —
 * the DukanLocker login flow authenticates by emailId and password.
 *
 * <p>Returns a Spring Security {@link UserDetails} with the role mapped
 * to {@code ROLE_<role>} authorities so the
 * {@code SecurityConfig#requestMatchers(...).hasRole("ADMIN")} matcher
 * works without further translation.</p>
 *
 * <p>Disabled accounts get {@code enabled=false} on the UserDetails, which
 * Spring Security's {@code DaoAuthenticationProvider} honors at authenticate
 * time — they fail before reaching the password comparison, so a plain-text
 * "disabled" probe never even tries BCrypt on a stale hash.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email is required");
        }
        String normalized = email.trim().toLowerCase();
        User user = userRepository.findByEmailId(normalized)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + normalized));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmailId())
                .password(user.getPassword() == null ? "" : user.getPassword())
                .disabled(!user.isEnabled())
                .accountLocked(false)
                .credentialsExpired(false)
                .accountExpired(false)
                .authorities(List.of(
                        new SimpleGrantedAuthority(
                                com.shoplocker.fssai.security.JwtService.roleAuthority(user.getRole()))))
                .build();
    }
}
