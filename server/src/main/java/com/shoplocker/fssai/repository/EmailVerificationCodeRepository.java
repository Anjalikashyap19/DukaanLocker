package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findTopByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmail(String email);
}
