package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.OnboardingJob;
import com.shoplocker.fssai.event.AccountCredentialsEvent;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.OnboardingJobRepository;
import jakarta.activation.DataHandler;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails the credentials of an auto-onboarded owner.
 *
 * <p>Runs after the account transaction commits. When SMTP is not configured
 * ({@code spring.mail.*} unset, so Spring creates no {@link JavaMailSender})
 * the mail is skipped with a warning and the job detail records it — the
 * account itself stays valid.</p>
 */
@Service
public class CredentialMailService {

    private static final Logger log = LoggerFactory.getLogger(CredentialMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final OnboardingJobRepository jobRepository;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.onboarding.play-store-url:https://play.google.com/store/apps/details?id=com.iadv.dukaanlocker&pcampaignid=web_share}")
    private String playStoreUrl;

    public CredentialMailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                 OnboardingJobRepository jobRepository) {
        this.mailSenderProvider = mailSenderProvider;
        this.jobRepository = jobRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountCreated(AccountCredentialsEvent event) {
        if (event.emailId() == null || event.emailId().isBlank()) {
            return;
        }
        if (event.placeholderContact()) {
            log.info("Onboarding mail skipped for user {} (job {}): generated contact details",
                    event.userId(), event.jobId());
            annotate(event.jobId(), "credentials NOT emailed (generated contact details)");
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        // An empty spring.mail.host still satisfies Boot's condition and yields a
        // sender that can only fail, so the host itself decides availability.
        if (sender == null || mailHost == null || mailHost.isBlank()) {
            log.warn("Onboarding mail skipped for {}: spring.mail.host is not configured", event.emailId());
            annotate(event.jobId(), "credentials NOT emailed (SMTP not configured)");
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            message.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(event.emailId()));
            if (fromAddress != null && !fromAddress.isBlank()) {
                message.setFrom(new InternetAddress(fromAddress));
            }
            message.setSubject("Your DukaanLocker account is ready", "UTF-8");
            message.setContent(buildContent(event));
            sender.send(message);
            log.info("Onboarding credentials emailed to user {} (job {})", event.userId(), event.jobId());
            annotate(event.jobId(), "credentials emailed");
        } catch (Exception e) {
            log.error("Onboarding credentials email failed for user {}: {}", event.userId(), e.getMessage());
            annotate(event.jobId(), "credentials email failed: " + e.getMessage());
        }
    }

    /** Sends a one-time code for a self-service email change. */
    public void sendVerificationCode(String email, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailHost == null || mailHost.isBlank()) {
            throw new FssaiException("Email verification is not configured", FailureCode.INVALID_REQUEST);
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            message.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(email));
            if (fromAddress != null && !fromAddress.isBlank()) {
                message.setFrom(new InternetAddress(fromAddress));
            }
            message.setSubject("DukaanLocker email verification code", "UTF-8");

            MimeMultipart alternative = new MimeMultipart("alternative");
            MimeBodyPart text = new MimeBodyPart();
            text.setText("Your DukaanLocker email verification code is " + code
                    + ". It is valid for 10 minutes. Ignore this mail if you did not request it.", "UTF-8");
            MimeBodyPart html = new MimeBodyPart();
            html.setContent("<!DOCTYPE html><html><body style=\"margin:0;background:#f6f8fb;padding:24px;"
                            + "font-family:Arial,Helvetica,sans-serif;color:#222\">"
                            + "<div style=\"max-width:560px;margin:auto;background:#fff;border:1px solid #e5e9f0;"
                            + "border-radius:12px;padding:28px\">"
                            + "<h2 style=\"margin:0 0 12px;font-size:20px;color:#1b3a6b\">DukaanLocker</h2>"
                            + "<p style=\"margin:0 0 16px\">Your email verification code is:</p>"
                            + "<p style=\"margin:0 0 16px;font-size:26px;font-weight:700;letter-spacing:4px\">"
                            + code + "</p>"
                            + "<p style=\"margin:0 0 16px\">This code is valid for 10 minutes.</p>"
                            + "<p style=\"margin:0;color:#777;font-size:12px\">Ignore this mail if you did not "
                            + "request an email change.</p></div></body></html>",
                    "text/html; charset=UTF-8");
            alternative.addBodyPart(text);
            alternative.addBodyPart(html);
            message.setContent(alternative);
            sender.send(message);
            log.info("Email verification code sent to {}", email);
        } catch (Exception e) {
            log.error("Could not send email verification code to {}: {}", email, e.getMessage());
            throw new FssaiException("Could not send the verification email", FailureCode.INVALID_REQUEST);
        }
    }

    private void annotate(Long jobId, String note) {
        if (jobId == null) {
            return;
        }
        try {
            OnboardingJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                return;
            }
            String detail = job.getDetail() == null ? note : job.getDetail() + "; " + note;
            job.setDetail(detail.length() > 500 ? detail.substring(0, 500) : detail);
            jobRepository.save(job);
        } catch (Exception e) {
            log.warn("Could not annotate onboarding job {}: {}", jobId, e.getMessage());
        }
    }

    /** multipart/related(logo) wrapping multipart/alternative(text, html). */
    private MimeMultipart buildContent(AccountCredentialsEvent event) throws Exception {
        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(buildPlainText(event), "UTF-8");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(buildHtml(event), "text/html; charset=UTF-8");
        alternative.addBodyPart(textPart);
        alternative.addBodyPart(htmlPart);

        MimeMultipart related = new MimeMultipart("related");
        MimeBodyPart body = new MimeBodyPart();
        body.setContent(alternative);
        related.addBodyPart(body);

        MimeBodyPart logo = new MimeBodyPart();
        byte[] image = new ClassPathResource("branding/dukaanlocker-logo.png").getContentAsByteArray();
        logo.setDataHandler(new DataHandler(new ByteArrayDataSource(image, "image/png")));
        logo.setContentID("<dukaanlockerLogo>");
        logo.setDisposition(MimeBodyPart.INLINE);
        logo.setFileName("dukaanlocker-logo.png");
        related.addBodyPart(logo);
        return related;
    }

    private String buildPlainText(AccountCredentialsEvent event) {
        String name = event.ownerName() == null ? "there" : event.ownerName().trim();
        String shop = event.shopName() == null ? "your business" : event.shopName().trim();
        return "Welcome to DukaanLocker!\n"
                + "A secure business document vault for your business.\n\n"
                + "Hello " + name + ",\n"
                + "Your account for " + shop + " has been created from the FSSAI licence on your "
                + "certificate. Your FSSAI licence has been renewed successfully — you can see it "
                + "in the DukaanLocker app.\n\n"
                + "Your login credentials\n"
                + "Login ID: " + event.emailId() + "\n"
                + "Password: " + event.rawPassword() + "\n\n"
                + "Please change your password for security reasons.\n\n"
                + "Download the app: " + playStoreUrl + "\n\n"
                + "This is a system-generated mail. Please do not reply.\n"
                + "A product of IndiaAdvocacy. Copyright © 2026. All rights reserved.\n";
    }

    String buildHtml(AccountCredentialsEvent event) {
        String name = HtmlUtils.htmlEscape(event.ownerName() == null ? "there" : event.ownerName());
        String shop = HtmlUtils.htmlEscape(event.shopName() == null ? "your business" : event.shopName());
        String email = HtmlUtils.htmlEscape(event.emailId());
        String password = HtmlUtils.htmlEscape(event.rawPassword());
        String store = HtmlUtils.htmlEscape(playStoreUrl);

        return "<!DOCTYPE html><html><body style=\"margin:0\">"
                + "<div style=\"font-family:Arial,Helvetica,sans-serif;background:#f6f8fb;padding:24px;color:#222\">"
                + "<div style=\"max-width:600px;margin:auto;background:#fff;border:1px solid #e5e9f0;"
                + "border-radius:12px;padding:28px\">"
                + "<div style=\"text-align:center;border-bottom:1px solid #e5e9f0;padding-bottom:20px;margin-bottom:24px\">"
                + "<img src=\"cid:dukaanlockerLogo\" alt=\"DukaanLocker\" width=\"72\" height=\"72\" "
                + "style=\"display:block;margin:0 auto 10px\"/>"
                + "<div style=\"font-size:22px;font-weight:700;color:#1b3a6b\">DukaanLocker</div>"
                + "<div style=\"font-size:13px;color:#667\">A secure business document vault for your business</div>"
                + "</div>"
                + "<h2 style=\"margin:0 0 12px;font-size:20px\">Welcome to DukaanLocker, " + name + "!</h2>"
                + "<p style=\"margin:0 0 16px\">Your account for <strong>" + shop + "</strong> has been created "
                + "from the FSSAI licence on your certificate. Your FSSAI licence has been renewed successfully "
                + "— you can see it <a href=\"" + store + "\">here</a> in the DukaanLocker app.</p>"
                + "<div style=\"background:#f1f5fb;border:1px solid #dce4f2;border-radius:8px;padding:16px;margin:0 0 16px\">"
                + "<p style=\"margin:0 0 8px;font-weight:700\">Your login credentials</p>"
                + "<p style=\"margin:0 0 6px\"><strong>Login ID:</strong> " + email + "</p>"
                + "<p style=\"margin:0\"><strong>Password:</strong> " + password + "</p>"
                + "</div>"
                + "<p style=\"margin:0 0 16px;color:#a33\"><strong>Please change your password for security reasons.</strong></p>"
                + "<p style=\"margin:0 0 16px\">Download the app: <a href=\"" + store + "\">" + store + "</a></p>"
                + "<div style=\"border-top:1px solid #e5e9f0;margin-top:24px;padding-top:16px;font-size:12px;color:#777\">"
                + "<p style=\"margin:0 0 6px\">This is a system-generated mail. Please do not reply.</p>"
                + "<p style=\"margin:0\">A product of IndiaAdvocacy. Copyright &copy; 2026. All rights reserved.</p>"
                + "</div></div></div></body></html>";
    }
}
