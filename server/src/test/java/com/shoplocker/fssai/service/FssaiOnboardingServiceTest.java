package com.shoplocker.fssai.service;

import com.shoplocker.fssai.repository.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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

    @Test
    @DisplayName("reads the real contact mobile from the licence lookup JSON")
    void looksUpContactMobile() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"details\":{\"contactMobile\":\"8682981642\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            FssaiOnboardingService service = new FssaiOnboardingService(null, null, null, null,
                    null, null, null, null, null, null);
            ReflectionTestUtils.setField(service, "contactLookupUrl",
                    "http://localhost:" + server.getAddress().getPort() + "/?lic_num=");

            assertThat(service.lookupMobile("22425449000148")).isEqualTo("8682981642");
        } finally {
            server.stop(0);
        }
    }
}
