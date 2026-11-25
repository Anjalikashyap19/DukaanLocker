package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.OnboardingJob;
import com.shoplocker.fssai.event.AccountCredentialsEvent;
import com.shoplocker.fssai.repository.OnboardingJobRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    @Value("${app.onboarding.play-store-url:https://play.google.com/store/apps/details?id=com.iadv.dukaanlocker}")
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
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(event.emailId());
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setSubject("Your DukaanLocker account is ready");
            helper.setText(buildHtml(event), true);
            sender.send(message);
            log.info("Onboarding credentials emailed to user {} (job {})", event.userId(), event.jobId());
            annotate(event.jobId(), "credentials emailed");
        } catch (Exception e) {
            log.error("Onboarding credentials email failed for user {}: {}", event.userId(), e.getMessage());
            annotate(event.jobId(), "credentials email failed: " + e.getMessage());
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

    private String buildHtml(AccountCredentialsEvent event) {
        String name = HtmlUtils.htmlEscape(event.ownerName() == null ? "there" : event.ownerName());
        String shop = HtmlUtils.htmlEscape(event.shopName() == null ? "your business" : event.shopName());
        String email = HtmlUtils.htmlEscape(event.emailId());
        String password = HtmlUtils.htmlEscape(event.rawPassword());
        String store = HtmlUtils.htmlEscape(playStoreUrl);

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:15px;color:#222;line-height:1.5\">"
                + "<p>Hello " + name + ",</p>"
                + "<p>Your DukaanLocker account for <strong>" + shop + "</strong> has been created using "
                + "the FSSAI licence printed on your certificate.</p>"
                + "<p><strong>Login email:</strong> " + email + "<br/>"
                + "<strong>Password:</strong> " + password + "</p>"
                + "<p>Please change this password after your first login.</p>"
                + "<p><a href=\"" + store + "\">Download the DukaanLocker app from Google Play</a></p>"
                + "<p>Regards,<br/>Team DukaanLocker</p>"
                + "</div>";
    }
}
