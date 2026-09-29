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
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test config (src/test/resources/application.properties):
 * slots=00:00,00:00,00:00 (always due), jitter=0, min-per-day=max-per-day=3.
 *
 * Behavior under test: one run serves every due slot (3), one notification per
 * slot about a distinct missing doc, daily quota is a hard cap, per-doc
 * eligibility uses a calendar-day window, and each send carries a slot dedupe
 * key so repeated runs / logins never double-fire a slot.
 */
@SpringBootTest
@Transactional
@DisplayName("DocumentMissingScheduler drip tests")
class DocumentMissingSchedulerTest {

    @Autowired private DocumentMissingScheduler scheduler;
    @Autowired private UserRepository userRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RequiredDocumentService requiredDocumentService;
    @Autowired private NotificationService notificationService;

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
    @DisplayName("One run serves all due slots: quota notifications, distinct docs, slot dedupe keys")
    void oneRunServesAllDueSlots() {
        // GROCERY/SMALL requires 6 docs, none uploaded -> plenty of candidates.
        // Slots 00:00 x3 are always due, target=3.
        int sent = scheduler.checkMissingDocumentsForOwner(owner.getId());

        assertThat(sent).isEqualTo(3);
        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(3);
        assertThat(notifs)
                .allMatch(n -> "MISSING_DOCUMENT".equals(n.getType()))
                .allMatch(n -> "MISSING_DOC".equals(n.getCategory()))
                .allMatch(n -> n.getReferenceId().equals(shop.getId()))
                .allMatch(n -> n.getMetadata() != null && requiredDocNames().contains(n.getMetadata()))
                .allMatch(n -> n.getDedupeKey() != null
                        && n.getDedupeKey().startsWith("missing:" + LocalDate.now() + ":slot:"));
        assertThat(notifs.stream().map(Notification::getMetadata).distinct()).hasSize(3);
    }

    @Test
    @DisplayName("Repeated runs never exceed the daily target (slot dedupe)")
    void repeatedRunsAreCappedByDailyTarget() {
        for (int run = 0; run < 4; run++) {
            scheduler.checkMissingDocumentsForOwner(owner.getId());
        }

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(3);
        assertThat(notifs.stream().map(Notification::getMetadata).distinct()).hasSize(3);
    }

    @Test
    @DisplayName("A doc already reminded about today is skipped and counts toward the daily quota")
    void perDocGateSkipsDocAlreadyNotifiedToday() {
        precreateMissingDocNotification("GST");

        // The pre-created reminder already consumes 1 of today's quota of 3
        int sent = scheduler.checkMissingDocumentsForOwner(owner.getId());

        assertThat(sent).isEqualTo(2);
        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(3);
        // GST appears exactly once (the pre-created one) - never re-sent today
        assertThat(notifs.stream().filter(n -> "GST".equals(n.getMetadata())).count()).isEqualTo(1);
        // the drip sends are about other distinct docs
        List<String> dripDocs = notifs.stream()
                .filter(n -> n.getDedupeKey() != null)
                .map(Notification::getMetadata)
                .toList();
        assertThat(dripDocs).hasSize(2).doesNotContain("GST");
    }

    @Test
    @DisplayName("Yesterday's reminder does not block today's rotation")
    void staleReminderOutsideCalendarDayBecomesEligibleAgain() {
        Notification stale = precreateMissingDocNotification("GST");

        // created_at is updatable=false -> backdate via native update
        entityManager.createNativeQuery("UPDATE notifications SET created_at = :ts WHERE id = :id")
                .setParameter("ts", Timestamp.valueOf(LocalDateTime.now().minusHours(48)))
                .setParameter("id", stale.getId())
                .executeUpdate();

        int sent = scheduler.checkMissingDocumentsForOwner(owner.getId());

        // stale one no longer blocks nor counts toward today's quota
        assertThat(sent).isEqualTo(3);
        assertThat(notificationRepository.findAll()).hasSize(4);
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

        assertThat(sent).isEqualTo(3);
        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).hasSize(3);
        assertThat(notifs).allMatch(n -> n.getReferenceId().equals(shop.getId()));
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(otherOwner.getId())).isEmpty();
    }

    @Test
    @DisplayName("Global run fans out to every owner")
    void globalRunCoversEveryOwner() {
        scheduler.checkMissingDocuments();

        assertThat(notificationRepository.findAll())
                .hasSize(3)
                .allMatch(n -> n.getUser().getId().equals(owner.getId()));
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(owner.getId())).hasSize(3);
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

        int sent = scheduler.checkMissingDocumentsForOwner(owner.getId());

        assertThat(sent).isZero();
        assertThat(notificationRepository.findAll()).isEmpty();
    }
}
