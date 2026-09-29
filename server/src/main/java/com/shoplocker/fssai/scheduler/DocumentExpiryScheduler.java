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
 *   expired   daily reminders, capped at expired-max-days
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

    public DocumentExpiryScheduler(DocumentRepository documentRepository,
                                    ShopRepository shopRepository,
                                    NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.shopRepository = shopRepository;
        this.notificationService = notificationService;
    }

    public void checkExpiringDocuments() {
        log.info("Running document expiry check scheduler...");

        List<Long> stages = parseStages();
        List<Document> allDocs = documentRepository.findAll();
        int notificationsSent = 0;

        for (Document doc : allDocs) {
            if (doc.getExpiryDate() == null) continue;
            if (doc.getStatus() == DocumentStatus.NOT_UPLOADED) continue;
            try {
                if (processDocument(doc, stages)) notificationsSent++;
            } catch (Exception e) {
                log.error("Expiry check failed for document {} (shop {}): {}",
                        doc.getId(), doc.getShop() != null ? doc.getShop().getId() : null,
                        e.getMessage(), e);
            }
        }

        log.info("Document expiry check completed. Sent {} notifications.", notificationsSent);
    }

    private boolean processDocument(Document doc, List<Long> stages) {
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(today, doc.getExpiryDate().toLocalDate());

        if (days > stages.get(0)) return false;                    // still too far out

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
            if (Math.abs(days) > expiredMaxDays) return false;      // stop nagging after the cap
            stage = "EXPIRED";
            payloadStage = "EXPIRED";
            type = "EXPIRED";
            dedupeKey = "expiry:" + doc.getId() + ":X" + Math.abs(days);
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

        // Keep document status in sync with what the user is being told
        if (days < 0 && doc.getStatus() != DocumentStatus.EXPIRED) {
            doc.setStatus(DocumentStatus.EXPIRED);
            documentRepository.save(doc);
        } else if (days <= stages.get(stages.size() - 1) && days >= 0
                && doc.getStatus() != DocumentStatus.EXPIRING_SOON) {
            doc.setStatus(DocumentStatus.EXPIRING_SOON);
            documentRepository.save(doc);
        }

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
