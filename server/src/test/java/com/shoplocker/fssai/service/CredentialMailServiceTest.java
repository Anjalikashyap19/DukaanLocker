package com.shoplocker.fssai.service;

import com.shoplocker.fssai.event.AccountCredentialsEvent;
import com.shoplocker.fssai.repository.OnboardingJobRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CredentialMailService template")
class CredentialMailServiceTest {

    @Test
    @DisplayName("contains logo, welcome, credentials and footer")
    void buildsTemplate() {
        CredentialMailService service = new CredentialMailService(
                null, mock(OnboardingJobRepository.class), mock(WhatsAppService.class));
        ReflectionTestUtils.setField(service, "playStoreUrl",
                "https://play.google.com/store/apps/details?id=com.iadv.dukaanlocker&pcampaignid=web_share");

        String html = service.buildHtml(new AccountCredentialsEvent(
                1L, 2L, "owner@example.com", "TEST@123", "OM PRAKASH", "NEW TYRES HOUSE",
                "1234567890", false, false, "9876543210"));

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

    @Test
    @DisplayName("does not email or WhatsApp when both contact details are generated")
    void skipsGeneratedContact() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        WhatsAppService whatsApp = mock(WhatsAppService.class);
        CredentialMailService service = new CredentialMailService(
                provider, mock(OnboardingJobRepository.class), whatsApp);
        ReflectionTestUtils.setField(service, "mailHost", "smtp.gmail.com");

        service.onAccountCreated(new AccountCredentialsEvent(
                1L, 2L, "generated@gmail.com", "TEST@123", "OWNER", "SHOP", "1234567890",
                true, true, "9876543210"));

        verify(sender, never()).send(any(MimeMessage.class));
        verify(whatsApp, never()).sendTemplate(
                anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sends WhatsApp when email is generated but mobile is real")
    void sendsWhatsAppWhenRealMobileNoEmail() {
        WhatsAppService whatsApp = mock(WhatsAppService.class);
        when(whatsApp.sendTemplate(
                eq(1L),
                eq("9876543210"),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(true);
        CredentialMailService service = new CredentialMailService(
                mock(ObjectProvider.class), mock(OnboardingJobRepository.class), whatsApp);

        service.onAccountCreated(new AccountCredentialsEvent(
                1L, 2L, "", "TEST@123", "OWNER", "SHOP", "1234567890",
                true, false, "9876543210"));

        verify(whatsApp).sendTemplate(
                eq(1L),
                eq("9876543210"),
                anyString(),
                anyString(),
                anyString());
    }
}
