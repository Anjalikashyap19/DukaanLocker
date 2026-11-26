package com.shoplocker.fssai.service;

import com.shoplocker.fssai.event.AccountCredentialsEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CredentialMailService template")
class CredentialMailServiceTest {

    @Test
    @DisplayName("contains logo, welcome, credentials and footer")
    void buildsTemplate() {
        CredentialMailService service = new CredentialMailService(null, null);
        ReflectionTestUtils.setField(service, "playStoreUrl",
                "https://play.google.com/store/apps/details?id=com.iadv.dukaanlocker&pcampaignid=web_share");

        String html = service.buildHtml(new AccountCredentialsEvent(
                1L, 2L, "owner@example.com", "TEST@123", "OM PRAKASH", "NEW TYRES HOUSE", "1234567890"));

        assertThat(html)
                .contains("cid:dukaanlockerLogo")
                .contains("DukaanLocker")
                .contains("Welcome to DukaanLocker")
                .contains("A secure business document vault for your business")
                .contains("Login ID:")
                .contains("owner@example.com")
                .contains("TEST@123")
                .contains("Please change your password for security reasons.")
                .contains("renewed successfully")
                .contains("https://play.google.com/store/apps/details?id=com.iadv.dukaanlocker&amp;pcampaignid=web_share")
                .contains("system-generated mail")
                .contains("A product of IndiaAdvocacy")
                .contains("Copyright &copy; 2026");
    }
}
