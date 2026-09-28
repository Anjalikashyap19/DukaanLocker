package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.NotificationRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.service.NotificationService;
import com.shoplocker.fssai.service.RequiredDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DocumentMissingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentMissingScheduler.class);

    private final DocumentRepository documentRepository;
    private final ShopRepository shopRepository;
    private final RequiredDocumentService requiredDocumentService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    @Value("${notification.missing-doc.max-per-run:3}")
    private int maxPerRun;

    @Value("${notification.missing-doc.max-per-day:5}")
    private int maxPerDay;

    @Value("${notification.missing-doc.min-interval-hours:24}")
    private long minIntervalHours;

    @Value("${notification.missing-doc.rotation-strategy:ROUND_ROBIN}")
    private String rotationStrategy;

    public DocumentMissingScheduler(DocumentRepository documentRepository,
                                     ShopRepository shopRepository,
                                     RequiredDocumentService requiredDocumentService,
                                     NotificationService notificationService,
                                     NotificationRepository notificationRepository) {
        this.documentRepository = documentRepository;
        this.shopRepository = shopRepository;
        this.requiredDocumentService = requiredDocumentService;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    public void checkMissingDocuments() {
        log.info("Running missing document check scheduler...");

        List<Shop> allShops = shopRepository.findAll();
        int notificationsSent = notifyMissingDocuments(allShops);

        log.info("Missing document check completed. Sent {} notifications.", notificationsSent);
    }

    /**
     * Login-time trigger: sends this owner's missing-document notifications only.
     * Together with {@link #checkMissingDocuments()} (3 runs per day) every logged-in
     * owner gets at least 3 - at most 5 - missing-document notifications per day,
     * each about a different document, never the same doc twice inside the window.
     */
    public int checkMissingDocumentsForOwner(Long ownerId) {
        List<Shop> shops = shopRepository.findByOwnerId(ownerId);
        return notifyMissingDocuments(shops);
    }

    private int notifyMissingDocuments(List<Shop> shops) {
        int notificationsSent = 0;
        Instant intervalStart = Instant.now().minus(minIntervalHours, ChronoUnit.HOURS);

        for (Shop shop : shops) {
            try {
                notificationsSent += notifyForShop(shop, intervalStart);
            } catch (Exception e) {
                log.error("Missing-document check failed for shop {} (owner {}): {}",
                        shop.getId(), shop.getOwner().getId(), e.getMessage(), e);
            }
        }
        return notificationsSent;
    }

    private int notifyForShop(Shop shop, Instant intervalStart) {
        Long ownerId = shop.getOwner().getId();
        Set<DocumentType> requiredDocs = requiredDocumentService.getRequiredDocuments(
                shop.getCategory(), shop.getScale());

        List<Document> existingDocs = documentRepository.findByShopId(shop.getId());
        Set<DocumentType> uploadedTypes = new HashSet<>();
        for (Document doc : existingDocs) {
            if (doc.getStatus() != DocumentStatus.NOT_UPLOADED) {
                uploadedTypes.add(doc.getDocumentType());
            }
        }

        List<DocumentType> missingDocs = requiredDocs.stream()
                .filter(dt -> !uploadedTypes.contains(dt))
                .sorted(Comparator.comparing(Enum::ordinal))
                .collect(Collectors.toList());

        if (missingDocs.isEmpty()) {
            return 0;
        }

        // Per-document gate: skip docs already notified for this shop inside the
        // window (metadata holds the documentType), so the same doc is never repeated
        // while other missing docs are still eligible.
        List<DocumentType> candidates = missingDocs.stream()
                .filter(dt -> !notificationRepository
                        .existsByUserIdAndTypeAndReferenceIdAndMetadataAndCreatedAtAfter(
                                ownerId, "MISSING_DOCUMENT", shop.getId(), dt.name(), intervalStart))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.debug("Skipping shop {} (owner {}) - every missing doc already notified within last {} hours",
                    shop.getId(), ownerId, minIntervalHours);
            return 0;
        }

        // Rolling daily quota per shop: caps the total (logins + scheduled runs
        // combined) at maxPerDay notifications inside the window.
        long alreadySent = notificationRepository.countByUserIdAndTypeAndReferenceIdAndCreatedAtAfter(
                ownerId, "MISSING_DOCUMENT", shop.getId(), intervalStart);
        int remainingQuota = (int) Math.max(0, maxPerDay - alreadySent);
        if (remainingQuota == 0) {
            log.debug("Skipping shop {} (owner {}) - daily quota of {} missing-doc notifications reached",
                    shop.getId(), ownerId, maxPerDay);
            return 0;
        }

        int toNotify = Math.min(maxPerRun, Math.min(remainingQuota, candidates.size()));
        List<DocumentType> selectedDocs = selectDocumentsToNotify(candidates, toNotify);

        int notificationsSent = 0;
        for (DocumentType required : selectedDocs) {
            String docName = formatDocumentName(required);
            String shopName = shop.getShopName();

            String title = "\uD83D\uDCC4 Missing " + docName;
            String body = String.format("Your %s needs a %s to stay compliant. Tap to upload now!",
                    shopName, docName);

            notificationService.sendPushAndCreateNotification(
                    ownerId, title, body, "MISSING_DOCUMENT", shop.getId(),
                    Map.of("shopId", shop.getId().toString(), "documentType", required.name())
            );
            notificationsSent++;
        }
        return notificationsSent;
    }

    private List<DocumentType> selectDocumentsToNotify(List<DocumentType> missingDocs, int count) {
        if (missingDocs.size() <= count) {
            return missingDocs;
        }

        switch (rotationStrategy.toUpperCase()) {
            case "OLDEST_FIRST" -> {
                // Oldest-first would require tracking when each doc went missing;
                // without that data, fall back to round-robin
                return missingDocs.subList(0, count);
            }
            case "ROUND_ROBIN" -> {
                // Use day-of-year as rotation offset so each day picks the next doc
                long dayOffset = ChronoUnit.DAYS.between(
                        LocalDate.of(1970, 1, 1), LocalDate.now());
                int offset = (int) (dayOffset % missingDocs.size());
                return pickWithOffset(missingDocs, count, offset);
            }
            default -> {
                log.warn("Unknown rotation strategy '{}', defaulting to ROUND_ROBIN", rotationStrategy);
                return pickWithOffset(missingDocs, count, 0);
            }
        }
    }

    private List<DocumentType> pickWithOffset(List<DocumentType> docs, int count, int offset) {
        List<DocumentType> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = (offset + i) % docs.size();
            result.add(docs.get(idx));
        }
        return result;
    }

    private String formatDocumentName(DocumentType type) {
        return switch (type) {
            case GST -> "GST Certificate";
            case PAN -> "PAN Card";
            case FSSAI_FOOD_LICENSE -> "FSSAI License";
            case MSME_CERTIFICATE -> "MSME Certificate";
            case TRADE_LICENSE -> "Trade License";
            case SHOP_ESTABLISHMENT -> "Shop Establishment Certificate";
            case FIRE_SAFETY -> "Fire Safety Certificate";
            case POLLUTION_CONTROL -> "Pollution Control Certificate";
            case DRUG_LICENSE -> "Drug License";
            case IEC -> "IEC Certificate";
            case TRADEMARK -> "Trademark Certificate";
            case PROPERTY_TAX -> "Property Tax Receipt";
            case PROFESSIONAL_TAX -> "Professional Tax Certificate";
            case LABOUR_LICENSE -> "Labour License";
            case SHOP_INSURANCE -> "Shop Insurance";
            case AADHAAR -> "Aadhaar Card";
            case CUSTOM -> "Custom Document";
        };
    }
}