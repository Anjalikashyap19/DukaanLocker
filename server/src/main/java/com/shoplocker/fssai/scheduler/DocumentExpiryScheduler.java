package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Expiry-alert escalation for uploaded documents that carry an expiry date:
 *
 *   T-30  "will expire on {date} - renew now"
 *   T-15  "I think you forgot to renew - still have time"
 *   T-7   "very little time left - don't worry, we will help you"
 *   T-6..T-0  daily escalating reminders
 *   expired   daily reminders for expired-max-days, then a slow repeat every
 *             expired-repeat-days so a long-expired document is never
 *             permanently ignored
 *
 * Two entry points share this logic. The daytime cron runs the full ladder.
 * A second 24/7 cron runs with {@code expiredOnly=true}, which considers only
 * documents already past their expiry date, so a document uploaded as
 * already-expired is reported within minutes while the daily countdown
 * reminders never fire at night.
 *
 * Each stage/days is gated by a dedupe key (expiry:{docId}:T30 / :D5 / :X2),
 * so a stage fires whenever it is first seen - a missed calendar day is never
 * permanently skipped (the flaw of the previous exact-day implementation).
 *
 * referenceId is the SHOP id so in-app taps navigate to the right business;
 * the document id travels in the push payload as {@code documentId}.
 */
@Component
public class DocumentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryScheduler.class);

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final DocumentRepository documentRepository;
    private final ShopRepository shopRepository;
    private final NotificationService notificationService;

    /** Ascending or descending; parsed and sorted descending internally. */
    @Value("${notification.alert.stage-days:30,15,7}")
    private String stageDaysConfig;

    @Value("${notification.alert.expired-max-days:7}")
    private int expiredMaxDays;

    /** Gap between repeat reminders once a document is past the daily cap. */
    @Value("${notification.alert.expired-repeat-days:14}")
    private int expiredRepeatDays;

    public DocumentExpiryScheduler(DocumentRepository documentRepository,
                                    ShopRepository shopRepository,
                                    NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.shopRepository = shopRepository;
        this.notificationService = notificationService;
    }

    public void checkExpiringDocuments() {
        checkExpiringDocuments(false);
    }

    /**
     * @param expiredOnly when true, only documents already past their expiry date
     *                    are considered. Drives the 24/7 catch-up cron.
     */
    public void checkExpiringDocuments(boolean expiredOnly) {
        log.info("Running document expiry check scheduler{}...",
                expiredOnly ? " (catch-up: already expired only)" : "");

        List<Long> stages = parseStages();
        List<Document> allDocs = documentRepository.findAllWithShopAndOwner();
        int notificationsSent = 0;

        for (Document doc : allDocs) {
            if (doc.getExpiryDate() == null) continue;
            if (doc.getStatus() == DocumentStatus.NOT_UPLOADED) continue;
            try {
                if (processDocument(doc, stages, expiredOnly)) notificationsSent++;
            } catch (Exception e) {
                log.error("Expiry check failed for document {} (shop {}): {}",
                        doc.getId(), doc.getShop() != null ? doc.getShop().getId() : null,
                        e.getMessage(), e);
            }
        }

        log.info("Document expiry check completed. Sent {} notifications.", notificationsSent);
    }

    private boolean processDocument(Document doc, List<Long> stages, boolean expiredOnly) {
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(today, doc.getExpiryDate().toLocalDate());

        // Synced before every notification-policy early return: a document that
        // is already expired must read EXPIRED in the app even on the runs where
        // its reminder is suppressed by the repeat window or the dedupe key.
        syncStatus(doc, days, stages);

        if (days >= 0 && expiredOnly) return false;          // catch-up: expired only
        if (days > stages.get(0)) return false;              // still too far out

        Shop shop = doc.getShop();
        Long ownerId = shop.getOwner().getId();
        String docName = NotificationCopy.formatDocumentName(doc.getDocumentType());
        String shopName = shop.getShopName();
        String expiryDateStr = doc.getExpiryDate().toLocalDate().format(EXPIRY_FORMAT);
        int variant = NotificationCopy.variant(doc.getId(), today, Integer.MAX_VALUE);

        String stage;
        String type;
        String dedupeKey;
        String payloadStage;

        if (days < 0) {
            long overdue = Math.abs(days);
            stage = "EXPIRED";
            payloadStage = "EXPIRED";
            type = "EXPIRED";
            // Daily while inside the cap, then one reminder per repeat window
            // so a licence that expired weeks ago still surfaces.
            dedupeKey = overdue <= expiredMaxDays
                    ? "expiry:" + doc.getId() + ":X" + overdue
                    : "expiry:" + doc.getId() + ":XR" + expiredRepeatBucket(overdue);
        } else if (days < stages.get(stages.size() - 1)) {
            // Daily escalation window: below the smallest stage down to expiry day
            stage = days == 0 ? "DAILY_TODAY" : "DAILY";
            payloadStage = "D" + days;
            type = "EXPIRING_SOON";
            dedupeKey = "expiry:" + doc.getId() + ":D" + days;
        } else {
            long chosen = smallestStageAtLeast(stages, days);       // 30 | 15 | 7
            stage = "T" + chosen;
            payloadStage = "T" + chosen;
            type = "EXPIRING_SOON";
            dedupeKey = "expiry:" + doc.getId() + ":T" + chosen;
        }

        String title = NotificationCopy.expiryTitle(days, doc.getDocumentType());
        String body = "DAILY_TODAY".equals(stage)
                ? NotificationCopy.expiryBody("DAILY_TODAY", days, expiryDateStr,
                        doc.getDocumentType(), shopName, variant)
                : NotificationCopy.expiryBody(stage, days, expiryDateStr,
                        doc.getDocumentType(), shopName, variant);

        return notificationService.sendPushAndCreateNotification(
                ownerId, title, body, type, shop.getId(),
                Map.of("shopId", shop.getId().toString(),
                       "documentId", String.valueOf(doc.getId()),
                       "documentType", doc.getDocumentType().name(),
                       "daysLeft", String.valueOf(days),
                       "alertStage", payloadStage,
                       "route", "renewal"),
                dedupeKey
        );
    }

    /**
     * Mirrors {@link #expiredRepeatBucket}: 0 for the first repeat window, 1 for
     * the next. Stable for every run inside the same window, so the dedupe key
     * only changes when the next reminder is actually due.
     */
    private long expiredRepeatBucket(long overdue) {
        int repeat = Math.max(1, expiredRepeatDays);
        return (overdue - expiredMaxDays - 1) / repeat;
    }

    /**
     * Keeps document.status in step with the dates the user is shown. Runs ahead
     * of the notification policy so the badge is correct even when no reminder is
     * sent this run.
     */
    private void syncStatus(Document doc, long days, List<Long> stages) {
        DocumentStatus target;
        if (days < 0) {
            target = DocumentStatus.EXPIRED;
        } else if (days <= stages.get(stages.size() - 1)) {
            target = DocumentStatus.EXPIRING_SOON;
        } else {
            return;                                     // leave UPLOADED/VALID untouched
        }
        if (doc.getStatus() != target) {
            doc.setStatus(target);
            documentRepository.save(doc);
        }
    }

    private long smallestStageAtLeast(List<Long> stagesDesc, long days) {
        // stagesDesc is sorted descending; walk it and return the smallest >= days
        long chosen = stagesDesc.get(stagesDesc.size() - 1);
        for (long stage : stagesDesc) {
            if (stage >= days) chosen = stage;
        }
        return chosen;
    }

    private List<Long> parseStages() {
        List<Long> stages = new ArrayList<>();
        for (String raw : stageDaysConfig.split(",")) {
            String value = raw.trim();
            if (value.isEmpty()) continue;
            try {
                long days = Long.parseLong(value);
                if (days >= 0) stages.add(days);
            } catch (NumberFormatException e) {
                log.warn("Ignoring unparsable alert stage '{}'", value);
            }
        }
        if (stages.isEmpty()) {
            stages.addAll(List.of(30L, 15L, 7L));
        }
        stages.sort((a, b) -> Long.compare(b, a));                  // descending
        return stages;
    }
}
