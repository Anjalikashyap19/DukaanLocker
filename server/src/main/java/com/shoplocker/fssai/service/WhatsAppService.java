package com.shoplocker.fssai.service;

import com.shoplocker.fssai.repository.OnboardingJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Sends WhatsApp template messages to auto-onboarded FSSAI owners who have a
 * real mobile number (looked up from the licence API) but no real email on
 * their certificate.
 *
 * <p>Uses the Meta Cloud API format:</p>
 * <pre>{@code
 * {
 *   "messaging_product": "whatsapp",
 *   "to": "919876543210",
 *   "type": "template",
 *   "template": {
 *     "name": "dukaanlocker_account",
 *     "language": {"code": "en"},
 *     "components": [{
 *       "type": "body",
 *       "parameters": [
 *         {"type": "text", "text": "Owner Name"},
 *         {"type": "text", "text": "Shop Name"},
 *         {"type": "text", "text": "919876543210"},
 *         {"type": "text", "text": "https://play.google.com/..."}
 *       ]
 *     }]
 *   }
 * }
 * </pre>
 *
 * <p>Template parameters:</p>
 * <ol>
 *   <li>{{1}} — owner name</li>
 *   <li>{{2}} — shop name</li>
 *   <li>{{3}} — mobile number (from licence lookup API)</li>
 *   <li>{{4}} — app download link</li>
 * </ol>
 *
 * <p>The plaintext password is intentionally NOT included in WhatsApp — owners
 * set their own password in-app via OTP verification.</p>
 *
 * <p>When the WhatsApp provider is not configured, sends are skipped with a
 * warning and the job detail records it — the account stays valid.</p>
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final OnboardingJobRepository jobRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${app.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${app.whatsapp.api-url:}")
    private String apiUrl;

    @Value("${app.whatsapp.token:}")
    private String token;

    @Value("${app.whatsapp.template-name:}")
    private String templateName;

    @Value("${app.whatsapp.language-code:en}")
    private String languageCode;

    @Value("${app.onboarding.play-store-url:https://play.google.com/store/apps/details?id=com.iadv.dukaanlocker&pcampaignid=web_share}")
    private String playStoreUrl;

    public WhatsAppService(OnboardingJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Sends the WhatsApp template to the given mobile. Returns {@code true} when
     * the message was accepted by the provider, {@code false} when skipped or
     * failed.
     */
    public boolean sendTemplate(Long jobId, String mobile, String ownerName, String shopName,
                                String emailId) {
        if (!enabled) {
            log.info("WhatsApp disabled for job {}: app.whatsapp.enabled is false", jobId);
            annotate(jobId, "WhatsApp NOT sent (provider disabled)");
            return false;
        }
        String normalizedMobile = normalizeMobile(mobile);
        if (normalizedMobile == null || normalizedMobile.isBlank()) {
            log.info("WhatsApp skipped for job {}: no mobile number", jobId);
            annotate(jobId, "WhatsApp NOT sent (no mobile)");
            return false;
        }
        if (apiUrl == null || apiUrl.isBlank() || token == null || token.isBlank()
                || templateName == null || templateName.isBlank()) {
            log.warn("WhatsApp skipped for job {}: provider not configured", jobId);
            annotate(jobId, "WhatsApp NOT sent (provider not configured)");
            return false;
        }

        List<String> params = List.of(
                ownerName == null ? "there" : ownerName.trim(),
                shopName == null ? "your business" : shopName.trim(),
                normalizedMobile,
                playStoreUrl
        );

        try {
            String payload = buildPayload(normalizedMobile, params);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("WhatsApp template sent to {} (job {})", maskMobile(normalizedMobile), jobId);
                annotate(jobId, "WhatsApp sent to " + maskMobile(normalizedMobile));
                return true;
            }
            log.warn("WhatsApp provider returned HTTP {} for job {}: {}", response.statusCode(),
                    jobId, response.body());
            annotate(jobId, "WhatsApp send failed (HTTP " + response.statusCode() + ")");
            return false;
        } catch (Exception e) {
            log.error("WhatsApp send failed for job {}: {}", jobId, e.getMessage());
            annotate(jobId, "WhatsApp send failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Builds a Meta Cloud API compliant WhatsApp template payload.
     */
    private String buildPayload(String mobile, List<String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"messaging_product\":\"whatsapp\",");
        sb.append("\"to\":").append(quote(mobile)).append(",");
        sb.append("\"type\":\"template\",");
        sb.append("\"template\":{");
        sb.append("\"name\":").append(quote(templateName)).append(",");
        sb.append("\"language\":{\"code\":").append(quote(languageCode)).append("},");
        sb.append("\"components\":[{");
        sb.append("\"type\":\"body\",\"parameters\":[");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"type\":\"text\",\"text\":").append(quote(params.get(i))).append("}");
        }
        sb.append("]}]");
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Normalizes a mobile number to WhatsApp international format:
     * strips leading +, spaces, dashes; prefixes 91 (India) when missing.
     */
    private static String normalizeMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        String m = mobile.trim().replace("+", "").replaceAll("[\\s\\-]", "");
        if (m.isBlank()) {
            return null;
        }
        if (!m.startsWith("91") && m.length() == 10) {
            m = "91" + m;
        }
        return m;
    }

    private static String quote(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    private void annotate(Long jobId, String note) {
        if (jobId == null) {
            return;
        }
        try {
            var job = jobRepository.findById(jobId).orElse(null);
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

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "***";
        }
        return mobile.substring(0, 2) + "***" + mobile.substring(mobile.length() - 3);
    }
}
