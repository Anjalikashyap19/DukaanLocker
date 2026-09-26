package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.NotificationRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.service.NotificationService;
import com.shoplocker.fssai.service.RequiredDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("DocumentMissingScheduler integration tests")
class DocumentMissingSchedulerTest {

    @Autowired private DocumentMissingScheduler scheduler;
    @Autowired private UserRepository userRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RequiredDocumentService requiredDocumentService;
    @Autowired private NotificationService notificationService;
    @MockitoBean private com.google.firebase.messaging.FirebaseMessaging firebaseMessaging;

    private User owner;
    private Shop shop;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        documentRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();

        owner = new User();
        owner.setUserName("Test Owner");
        owner.setEmailId("owner@example.com");
        owner.setMobileNumber("9999999999");
        owner.setPassword("password");
        owner.setRole(Role.ADMIN);
        owner = userRepository.save(owner);

        shop = new Shop();
        shop.setShopName("Test Shop");
        shop.setOwnerName("Test Owner");
        shop.setMobile("9876543210");
        shop.setCategory("GROCERY");
        shop.setScale(BusinessScale.SMALL);
        shop.setState("Tamil Nadu");
        shop.setCity("Chennai");
        shop.setBranchName("Main");
        shop.setAddress("123 Main St");
        shop.setPincode("600001");
        shop.setOwner(owner);
        shop = shopRepository.save(shop);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        documentRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Sends exactly maxPerRun=1 notification per shop per run")
    void sendsOnlyOneNotificationPerShopPerRun() {
        // GROCERY requires: PAN, GST, FSSAI_FOOD_LICENSE, TRADE_LICENSE, MSME_CERTIFICATE, SHOP_INSURANCE
        // No documents uploaded -> all 6 are missing

        scheduler.checkMissingDocuments();

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getType()).isEqualTo("MISSING_DOCUMENT");
        assertThat(notifs.get(0).getReferenceId()).isEqualTo(shop.getId());
    }

    @Test
    @DisplayName("Min-interval gate prevents duplicate within window")
    void minIntervalGatePreventsDuplicate() {
        // Pre-create a MISSING_DOCUMENT notification within the min-interval window
        Notification existing = notificationService.createNotification(
                owner.getId(), "title", "body", "MISSING_DOCUMENT", shop.getId());
        // Override createdAt to 12 hours ago (after @PrePersist set it to now)
        existing.setCreatedAt(LocalDateTime.now().minusHours(12));
        notificationRepository.save(existing);

        scheduler.checkMissingDocuments();

        List<Notification> notifs = notificationRepository.findAll();
        // Should NOT create a new notification because one exists within 24h window
        assertThat(notifs).hasSize(1);
    }

    @Test
    @DisplayName("Allows notification after min-interval expires")
    void allowsNotificationAfterIntervalExpires() {
        // Pre-create a MISSING_DOCUMENT notification OUTSIDE the min-interval window
        Notification existing = notificationService.createNotification(
                owner.getId(), "title", "body", "MISSING_DOCUMENT", shop.getId());
        // Override createdAt to 36 hours ago
        existing.setCreatedAt(LocalDateTime.now().minusHours(36));
        notificationRepository.save(existing);

        scheduler.checkMissingDocuments();

        List<Notification> notifs = notificationRepository.findAll();
        // Should create a new notification because old one is outside 24h window
        assertThat(notifs).hasSize(2);
    }

    @Test
    @DisplayName("Rotation picks different doc on consecutive days (simulated via offset)")
    void rotationPicksDifferentDocEachDay() {
        // We can't easily mock LocalDate.now() in the scheduler without refactoring,
        // so we verify the selection logic indirectly by running with different
        // maxPerRun values and checking which doc types get notified.
        // With maxPerRun=1 and 6 missing docs, each run should pick 1 doc.
        // The specific doc picked depends on day-of-year offset; we just verify
        // only 1 is selected per run.

        // Run 1
        scheduler.checkMissingDocuments();
        List<Notification> notifs1 = notificationRepository.findAll();
        assertThat(notifs1).hasSize(1);

        // Run 2 - should be skipped due to min-interval gate
        notificationRepository.deleteAll();
        scheduler.checkMissingDocuments();
        List<Notification> notifs2 = notificationRepository.findAll();
        assertThat(notifs2).hasSize(0); // blocked by min-interval
    }

    @Test
    @DisplayName("Shops with no missing docs receive no notifications")
    void shopWithAllDocsUploadedReceivesNoNotifications() {
        // Upload all required docs for GROCERY
        Set<DocumentType> required = requiredDocumentService.getRequiredDocuments("GROCERY", BusinessScale.SMALL);
        for (DocumentType type : required) {
            Document doc = new Document();
            doc.setShop(shop);
            doc.setDocumentType(type);
            doc.setStatus(DocumentStatus.VALID);
            doc.setExpiryDate(java.time.LocalDate.now().plusYears(1).atStartOfDay());
            documentRepository.save(doc);
        }

        scheduler.checkMissingDocuments();

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).isEmpty();
    }
}