package com.shoplocker.fssai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the FSSAI endpoint configuration after it moved out of hardcoded
 * constants into {@code application.properties} / the environment.
 *
 * <p>The failure this protects against is subtle: a renamed or missing property
 * leaves the injected field {@code null}, the service still boots, the context
 * test still passes, and the NPE only surfaces at runtime as a failed FSSAI
 * fetch - the same class of silent breakage that let the API Setu placeholder
 * credentials sit unnoticed.</p>
 */
@SpringBootTest
@DisplayName("FSSAI endpoint configuration")
class FssaiVerificationConfigTest {

    @Autowired
    private FssaiVerificationService service;

    private String value(String field) {
        return (String) ReflectionTestUtils.getField(service, field);
    }

    @Test
    @DisplayName("both API endpoints resolve to a usable URL")
    void apiEndpointsAreConfigured() {
        assertThat(value("detailsApiUrl")).isNotBlank().startsWith("http");
        assertThat(value("licenseApiUrl")).isNotBlank().startsWith("http");
    }

    @Test
    @DisplayName("endpoints keep the trailing '=' the license number is appended to")
    void apiEndpointsKeepTrailingEquals() {
        // The license number is concatenated onto these URLs, so the "?" must
        // already be terminated by "=". Losing it yields
        // "lnchk.php?ln21221160000115", which the upstream cannot parse.
        assertThat(value("detailsApiUrl")).endsWith("=");
        assertThat(value("licenseApiUrl")).endsWith("=");
    }

    @Test
    @DisplayName("referer and PDF base URI resolve")
    void refererAndPdfBaseUriAreConfigured() {
        assertThat(value("referer")).isNotBlank().startsWith("http");
        assertThat(value("pdfBaseUri")).isNotBlank().startsWith("http");
    }
}
