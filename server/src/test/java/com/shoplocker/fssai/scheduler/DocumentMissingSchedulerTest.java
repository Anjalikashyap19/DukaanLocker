package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.NotificationRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.service.NotificationService;
import com.shoplocker.fssai.service.RequiredDocumentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test config (src/test/resources/application.properties):
 * max-per-run=1, max-per-day=3, min-interval-hours=24.
 *
 * Behavior under test: every scheduled run / login sends up to maxPerRun
 * DISTINCT missing docs (same doc never repeated inside the 24h window) until
 * the rolling daily quota (maxPerDay) is reached.
 */
@SpringBootTest
@Transactional
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

    @PersistenceContext
    private EntityManager entityManager;

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

    private Set<String> requiredDocNames() {
        return requiredDocumentService.getRequiredDocuments("GROCERY", BusinessScale.SMALL)
                .stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Notification precreateMissingDocNotification(String documentType) {
        return notificationService.createNotification(
                owner.getId(), "title", "body", "MISSING_DOCUMENT", shop.getId(), documentType);
    }

    @Test
    @DisplayName("Single run sends one MISSING_DOCUMENT notification carrying the doc type")
    void singleRunSendsOneDistinctDocNotification() {
        // GROCERY requires: PAN, GST, FSSAI_FOOD_LICENSE, TRADE_LICENSE,
        // MSME_CERTIFICATE, SHOP_INSURANCE -> none uploaded -> 6 missing
        scheduler.checkMissingDocuments();

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getType()).isEqualTo("MISSING_DOCUMENT");
        assertThat(notifs.get(0).getReferenceId()).isEqualTo(shop.getId());
        assertThat(notifs.get(0).getMetadata()).isIn(requiredDocNames());
    }

    @Test
    @DisplayName("Repeated runs cover different docs, capped by the daily quota")
    void repeatedRunsCoverDifferentDocsUpToDailyQuota() {
        // max-per-run=1, max-per-day=3 -> four runs produce exactly 3
        // notifications, all about different documents
        for (int run = 0; run < 4; run++) {
            scheduler.checkMissingDocuments();
        }

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(3);
        assertThat(notifs)
                .allMatch(n -> "MISSING_DOCUMENT".equals(n.getType()))
                .allMatch(n -> n.getMetadata() != null && requiredDocNames().contains(n.getMetadata()));
        assertThat(notifs.stream().map(Notification::getMetadata).distinct())
                .hasSize(3);
    }

    @Test
    @DisplayName("A doc already notified inside the window is never repeated")
    void perDocGateSkipsDocAlreadyNotifiedInWindow() {
        // GST was already reminded about recently -> it must be skipped while
        // another missing doc still gets the run's slot
        precreateMissingDocNotification("GST");

        scheduler.checkMissingDocuments();

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(2);
        long gstReminders = notifs.stream()
                .filter(n -> "GST".equals(n.getMetadata()))
                .count();
        assertThat(gstReminders).isEqualTo(1);
        assertThat(notifs.stream().map(Notification::getMetadata).distinct()).hasSize(2);
    }

    @Test
    @DisplayName("A stale reminder outside the window becomes eligible again")
    void allowsNotificationAfterWindowExpires() {
        Notification stale = precreateMissingDocNotification("GST");

        // created_at is updatable=false -> backdate via native update
        entityManager.createNativeQuery("UPDATE notifications SET created_at = :ts WHERE id = :id")
                .setParameter("ts", Timestamp.valueOf(LocalDateTime.now().minusHours(48)))
                .setParameter("id", stale.getId())
                .executeUpdate();

        scheduler.checkMissingDocuments();

        // stale one no longer blocks nor counts toward today's quota
        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(2);
    }

    @Test
    @DisplayName("Owner-scoped check only notifies that owner's shops")
    void ownerScopedCheckOnlyNotifiesTargetOwnersShops() {
        User otherOwner = new User();
        otherOwner.setUserName("Other Owner");
        otherOwner.setEmailId("other@example.com");
        otherOwner.setMobileNumber("8888888888");
        otherOwner.setPassword("password");
        otherOwner.setRole(Role.ADMIN);
        otherOwner = userRepository.save(otherOwner);

        Shop otherShop = new Shop();
        otherShop.setShopName("Other Shop");
        otherShop.setOwnerName("Other Owner");
        otherShop.setMobile("8765432109");
        otherShop.setCategory("GROCERY");
        otherShop.setScale(BusinessScale.SMALL);
        otherShop.setState("Tamil Nadu");
        otherShop.setCity("Chennai");
        otherShop.setBranchName("Main");
        otherShop.setAddress("456 Other St");
        otherShop.setPincode("600002");
        otherShop.setOwner(otherOwner);
        shopRepository.save(otherShop);

        int sent = scheduler.checkMissingDocumentsForOwner(owner.getId());

        assertThat(sent).isEqualTo(1);
        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getReferenceId()).isEqualTo(shop.getId());
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(otherOwner.getId())).isEmpty();
    }

    @Test
    @DisplayName("Login-time trigger reaches the daily minimum in one call")
    void loginTriggerReachesDailyMinimum() {
        // Simulates the AuthService login hook running where the scheduler
        // can fire: quota allows maxPerDay=3 per window for this owner
        int first = scheduler.checkMissingDocumentsForOwner(owner.getId());
        // further logins same day keep adding distinct docs up to the quota
        int second = scheduler.checkMissingDocumentsForOwner(owner.getId());
        int third = scheduler.checkMissingDocumentsForOwner(owner.getId());
        int fourth = scheduler.checkMissingDocumentsForOwner(owner.getId());

        assertThat(first + second + third).isEqualTo(3);
        assertThat(fourth).isZero();

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(3);
        assertThat(notifs.stream().map(Notification::getMetadata).distinct()).hasSize(3);
    }

    @Test
    @DisplayName("Shops with no missing docs receive no notifications")
    void shopWithAllDocsUploadedReceivesNoNotifications() {
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
