package com.shoplocker.fssai.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.service.FssaiOnboardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Polls the certificate feed and hands every new PDF to
 * {@link FssaiOnboardingService}.
 *
 * <p>The feed ({@code count_new_files.php}) has no cursor: it keeps listing the
 * same files until they are removed upstream, so idempotency lives in the
 * {@code onboarding_jobs.file_name} unique constraint, not here.</p>
 */
@Component
public class FssaiOnboardingScheduler {

    private static final Logger log = LoggerFactory.getLogger(FssaiOnboardingScheduler.class);

    private final FssaiOnboardingService onboardingService;
    private final ObjectMapper objectMapper;

    @Value("${app.onboarding.enabled:false}")
    private boolean enabled;

    @Value("${app.onboarding.feed-url:https://iadv.shop/count_new_files.php}")
    private String feedUrl;

    /** Files processed per poll; keeps a backlog from turning one run into a long job. */
    @Value("${app.onboarding.max-per-run:10}")
    private int maxPerRun;

    @Value("${app.onboarding.backfill-on-start:false}")
    private boolean backfillOnStart;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public FssaiOnboardingScheduler(FssaiOnboardingService onboardingService,
                                    ObjectMapper objectMapper) {
        this.onboardingService = onboardingService;
        this.objectMapper = objectMapper;
    }

    /** One-time, opt-in pass over all previously failed certificates. */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        if (!enabled || !backfillOnStart) {
            return;
        }
        try {
            List<String> names = onboardingService.requeueAllFailedJobs();
            for (String fileName : names) {
                onboardingService.process(fileName);
            }
            log.info("FSSAI onboarding backfill: {} failed job(s) re-queued", names.size());
        } catch (Exception e) {
            log.warn("FSSAI onboarding backfill failed: {}", e.getMessage());
        }
    }

    public void pollFeed() {
        if (!enabled) {
            return;
        }
        try {
            List<String> names = fetchPdfNames();
            int processed = 0;
            for (String fileName : names) {
                if (processed >= maxPerRun) {
                    log.info("FSSAI onboarding cap ({}) reached; remaining files wait for the next poll", maxPerRun);
                    break;
                }
                onboardingService.process(fileName);
                processed++;
            }
            log.info("FSSAI onboarding poll: {} file(s) handled", processed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("FSSAI onboarding poll interrupted");
        } catch (Exception e) {
            log.warn("FSSAI onboarding poll failed: {}", e.getMessage());
        }
    }

    /** End-of-day reconciliation: full feed, no per-run cap, plus today's failed jobs. */
    public void endOfDaySweep() {
        if (!enabled) {
            return;
        }
        try {
            Set<String> names = new LinkedHashSet<>(onboardingService.requeueFailedJobsForToday());
            names.addAll(fetchPdfNames());
            for (String fileName : names) {
                onboardingService.process(fileName);
            }
            log.info("FSSAI onboarding end-of-day sweep: {} file(s) checked", names.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("FSSAI onboarding end-of-day sweep interrupted");
        } catch (Exception e) {
            log.warn("FSSAI onboarding end-of-day sweep failed: {}", e.getMessage());
        }
    }

    List<String> fetchPdfNames() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("FSSAI feed returned HTTP {}", response.statusCode());
            return List.of();
        }
        JsonNode files = feedFiles(objectMapper.readTree(response.body()));
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode node : files) {
            String fileName = fileNameOf(node);
            if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
                names.add(fileName);
            }
        }
        return names;
    }

    /**
     * The live feed answers with a bare JSON array, while older captures wrapped
     * it in {@code {"new_files": [...]}}. Both shapes are accepted.
     */
    static JsonNode feedFiles(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        JsonNode files = root.path("new_files");
        return files.isArray() ? files : null;
    }

    /**
     * The feed has been seen as a plain string list; objects carry the name under
     * one of a few keys. Both shapes are accepted and only the basename is used.
     */
    private String fileNameOf(JsonNode node) {
        String raw;
        if (node.isTextual()) {
            raw = node.asText();
        } else {
            JsonNode name = node.path("file_name");
            if (name.isMissingNode()) name = node.path("filename");
            if (name.isMissingNode()) name = node.path("name");
            if (name.isMissingNode()) name = node.path("file");
            raw = name.isTextual() ? name.asText() : null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }
}
