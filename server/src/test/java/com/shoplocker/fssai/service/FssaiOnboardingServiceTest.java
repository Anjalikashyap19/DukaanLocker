package com.shoplocker.fssai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
