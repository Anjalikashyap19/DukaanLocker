package com.shoplocker.fssai.service;

import com.shoplocker.fssai.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FssaiOnboardingService password derivation")
class FssaiOnboardingServiceTest {

    @Test
    @DisplayName("uses the first name with honorifics removed")
    void derivesFromFirstName() {
        assertThat(FssaiOnboardingService.passwordFor("MR. KAMLA JANGALI YADAV")).isEqualTo("KAMLA@123");
        assertThat(FssaiOnboardingService.passwordFor("OM PRAKASH GOSWAMI")).isEqualTo("OM@123");
        assertThat(FssaiOnboardingService.passwordFor("Smt. Anita Sharma")).isEqualTo("Anita@123");
    }

    @Test
    @DisplayName("falls back when no usable name is present")
    void fallsBack() {
        assertThat(FssaiOnboardingService.passwordFor(null)).isEqualTo("User@123");
        assertThat(FssaiOnboardingService.passwordFor("  ")).isEqualTo("User@123");
    }

    @Test
    @DisplayName("generates a unique ownerfirstname@gmail.com placeholder")
    void placeholderEmail() {
        UserRepository users = mock(UserRepository.class);
        FssaiOnboardingService service = new FssaiOnboardingService(null, users, null, null,
                null, null, null, null, null, null);

        assertThat(service.generatePlaceholderEmail("SUMAN P S")).isEqualTo("suman@gmail.com");

        when(users.existsByEmailId("suman@gmail.com")).thenReturn(true);
        assertThat(service.generatePlaceholderEmail("SUMAN P S")).isEqualTo("suman1@gmail.com");
        assertThat(service.generatePlaceholderEmail("123")).isEqualTo("owner@gmail.com");
    }

    @Test
    @DisplayName("generates an unused 10-digit placeholder mobile")
    void placeholderMobile() {
        UserRepository users = mock(UserRepository.class);
        FssaiOnboardingService service = new FssaiOnboardingService(null, users, null, null,
                null, null, null, null, null, null);

        String mobile = service.generatePlaceholderMobile();
        assertThat(mobile).matches("^[6-9]\\d{9}$");
    }
}
