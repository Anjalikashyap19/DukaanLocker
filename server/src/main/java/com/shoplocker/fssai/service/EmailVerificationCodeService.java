package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.EmailVerificationCode;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.EmailVerificationCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/** Issues and verifies one-time codes sent to a new email address. */
@Service
public class EmailVerificationCodeService {

    private static final int CODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final int EXPIRY_MINUTES = 10;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final EmailVerificationCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialMailService mailService;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationCodeService(EmailVerificationCodeRepository repository,
                                        PasswordEncoder passwordEncoder,
                                        CredentialMailService mailService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    public void sendCode(String email) {
        String normalized = normalize(email);
        repository.findTopByEmailOrderByCreatedAtDesc(normalized).ifPresent(previous -> {
            if (previous.getCreatedAt() != null
                    && Duration.between(previous.getCreatedAt(), LocalDateTime.now()).getSeconds()
                    < RESEND_COOLDOWN_SECONDS) {
                throw new FssaiException("Please wait before requesting another code",
                        FailureCode.INVALID_REQUEST);
            }
        });

        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        EmailVerificationCode challenge = new EmailVerificationCode();
        challenge.setEmail(normalized);
        challenge.setCodeHash(passwordEncoder.encode(code.toString()));
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        challenge.setAttempts(0);
        challenge.setVerified(false);
        repository.save(challenge);
        mailService.sendVerificationCode(normalized, code.toString());
    }

    public void verifyCode(String email, String code) {
        String normalized = normalize(email);
        EmailVerificationCode challenge = repository.findTopByEmailOrderByCreatedAtDesc(normalized)
                .orElseThrow(() -> new FssaiException(
                        "Invalid or expired verification code. Please request a new one.",
                        FailureCode.INVALID_OTP));
        if (challenge.isVerified()) {
            return;
        }
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new FssaiException("Verification code expired. Please request a new one.",
                    FailureCode.OTP_EXPIRED);
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            throw new FssaiException("Too many incorrect attempts. Please request a new code.",
                    FailureCode.TOO_MANY_ATTEMPTS);
        }
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            repository.save(challenge);
            throw new FssaiException("Incorrect verification code.", FailureCode.INVALID_OTP);
        }
        challenge.setVerified(true);
        repository.save(challenge);
    }

    public void consume(String email) {
        repository.deleteByEmail(normalize(email));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
