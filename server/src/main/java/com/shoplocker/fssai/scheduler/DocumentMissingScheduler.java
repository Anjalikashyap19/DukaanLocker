package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.service.NotificationService;
import com.shoplocker.fssai.service.RequiredDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class DocumentMissingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentMissingScheduler.class);

    private final DocumentRepository documentRepository;
    private final ShopRepository shopRepository;
    private final RequiredDocumentService requiredDocumentService;
    private final NotificationService notificationService;

    public DocumentMissingScheduler(DocumentRepository documentRepository,
                                     ShopRepository shopRepository,
                                     RequiredDocumentService requiredDocumentService,
                                     NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.shopRepository = shopRepository;
        this.requiredDocumentService = requiredDocumentService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 30 9 * * ?")
    public void checkMissingDocuments() {
        log.info("Running missing document check scheduler...");

        List<Shop> allShops = shopRepository.findAll();
        int notificationsSent = 0;

        for (Shop shop : allShops) {
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

            for (DocumentType required : requiredDocs) {
                if (!uploadedTypes.contains(required)) {
                    // Check if we already sent this notification today
                    boolean alreadyNotified = notificationService.getNotifications(ownerId).stream()
                            .filter(n -> "MISSING_DOCUMENT".equals(n.getType()))
                            .filter(n -> n.getReferenceId() == null || n.getReferenceId() == shop.getId())
                            .filter(n -> n.getBody().contains(formatDocumentName(required)))
                            .filter(n -> n.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                            .findFirst()
                            .isPresent();

                    if (alreadyNotified) continue;

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
            }
        }

        log.info("Missing document check completed. Sent {} notifications.", notificationsSent);
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
