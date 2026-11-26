package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.EmailVerificationCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.EmailVerificationCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EmailVerificationCodeService")
class EmailVerificationCodeServiceTest {

    private final EmailVerificationCodeRepository repository = mock(EmailVerificationCodeRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final EmailVerificationCodeService service =
            new EmailVerificationCodeService(repository, encoder, mock(CredentialMailService.class));

    @Test
    @DisplayName("accepts the correct code and consumes it")
    void verifiesCode() {
        EmailVerificationCode challenge = challenge("123456", 0);
        when(repository.findTopByEmailOrderByCreatedAtDesc("user@gmail.com"))
                .thenReturn(Optional.of(challenge));

        service.verifyCode("user@gmail.com", "123456");

        assertThat(challenge.isVerified()).isTrue();
    }

    @Test
    @DisplayName("counts wrong attempts and rejects expired codes")
    void rejectsBadCodes() {
        EmailVerificationCode challenge = challenge("123456", 0);
        when(repository.findTopByEmailOrderByCreatedAtDesc("user@gmail.com"))
                .thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.verifyCode("user@gmail.com", "000000"))
                .isInstanceOf(FssaiException.class);
        assertThat(challenge.getAttempts()).isEqualTo(1);

        challenge.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.verifyCode("user@gmail.com", "123456"))
                .isInstanceOf(FssaiException.class);
    }

    private EmailVerificationCode challenge(String code, int attempts) {
        EmailVerificationCode challenge = new EmailVerificationCode();
        challenge.setEmail("user@gmail.com");
        challenge.setCodeHash(encoder.encode(code));
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        challenge.setAttempts(attempts);
        challenge.setVerified(false);
        return challenge;
    }
}
