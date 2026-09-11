package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class DocumentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryScheduler.class);

    private final DocumentRepository documentRepository;
    private final ShopRepository shopRepository;
    private final NotificationService notificationService;

    // Notification intervals before expiry (in days)
    private static final List<Long> NOTIFICATION_INTERVALS = List.of(30L, 23L, 16L, 9L, 2L);

    public DocumentExpiryScheduler(DocumentRepository documentRepository,
                                    ShopRepository shopRepository,
                                    NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.shopRepository = shopRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 9 * * ?")
    public void checkExpiringDocuments() {
        log.info("Running document expiry check scheduler...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysFromNow = now.plusDays(30);

        // Find all documents with expiry date within 30 days
        List<Document> allDocs = documentRepository.findAll();
        int notificationsSent = 0;

        for (Document doc : allDocs) {
            if (doc.getExpiryDate() == null) continue;
            if (doc.getStatus() == DocumentStatus.NOT_UPLOADED) continue;

            LocalDate expiryDate = doc.getExpiryDate().toLocalDate();
            long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);

            if (daysUntilExpiry < 0) {
                // Document has expired
                handleExpiredDocument(doc, daysUntilExpiry);
                notificationsSent++;
            } else if (daysUntilExpiry <= 30) {
                // Document is expiring soon
                handleExpiringSoonDocument(doc, daysUntilExpiry, now);
                notificationsSent++;
            }
        }

        log.info("Document expiry check completed. Processed {} documents.", notificationsSent);
    }

    private void handleExpiredDocument(Document doc, long daysExpired) {
        Shop shop = doc.getShop();
        Long ownerId = shop.getOwner().getId();
        String docName = formatDocumentName(doc.getDocumentType());
        String shopName = shop.getShopName();

        // Check if we already sent an expired notification today
        Optional<Notification> existing = notificationService.getNotifications(ownerId).stream()
                .filter(n -> "EXPIRED".equals(n.getType()) && doc.getId().equals(n.getReferenceId()))
                .filter(n -> n.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                .findFirst();

        if (existing.isPresent()) return;

        String title = "\u274C Document Expired!";
        String body = String.format("Your %s for %s has expired %d days ago. Upload the renewed copy ASAP to avoid penalties!",
                docName, shopName, Math.abs(daysExpired));

        notificationService.sendPushAndCreateNotification(
                ownerId, title, body, "EXPIRED", doc.getId(),
                Map.of("shopId", shop.getId().toString(), "documentType", doc.getDocumentType().name())
        );
    }

    private void handleExpiringSoonDocument(Document doc, long daysUntilExpiry, LocalDateTime now) {
        Shop shop = doc.getShop();
        Long ownerId = shop.getOwner().getId();
        String docName = formatDocumentName(doc.getDocumentType());
        String shopName = shop.getShopName();

        // Only send notification on specific intervals
        boolean shouldNotify = NOTIFICATION_INTERVALS.contains(daysUntilExpiry);
        if (!shouldNotify) return;

        // Check if we already sent this notification
        Optional<Notification> existing = notificationService.getNotifications(ownerId).stream()
                .filter(n -> "EXPIRING_SOON".equals(n.getType()) && doc.getId().equals(n.getReferenceId()))
                .filter(n -> n.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                .findFirst();

        if (existing.isPresent()) return;

        String urgency = daysUntilExpiry <= 7 ? "\uD83D\uDEA8" : "\u26A0\uFE0F";
        String title = urgency + " " + docName + " expiring soon";
        String body = String.format("Your %s for %s expires in %d days. Renew now to keep your business running smooth!",
                docName, shopName, daysUntilExpiry);

        // Update document status
        if (daysUntilExpiry <= 7) {
            doc.setStatus(DocumentStatus.EXPIRING_SOON);
            documentRepository.save(doc);
        }

        notificationService.sendPushAndCreateNotification(
                ownerId, title, body, "EXPIRING_SOON", doc.getId(),
                Map.of("shopId", shop.getId().toString(), "documentType", doc.getDocumentType().name(),
                        "daysLeft", String.valueOf(daysUntilExpiry))
        );
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
        };
    }
}
