package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shoplocker.fssai.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Used by CustomUserDetailsService#loadUserByUsername (security principal lookup) and by
    // AuthService.login() after a successful authentication. Spring Data derives the JPQL
    // automatically from the method name; `emailId` matches the User entity field exactly.
    Optional<User> findByEmailId(String emailId);

    // Used by AuthService.register() to reject duplicate emails (case-insensitive via
    // AuthService.normalizeEmail to lowercase before calling).
    boolean existsByEmailId(String emailId);

    // Used by AuthService.register() to reject duplicate mobile numbers.
    boolean existsByMobileNumber(String mobileNumber);

}