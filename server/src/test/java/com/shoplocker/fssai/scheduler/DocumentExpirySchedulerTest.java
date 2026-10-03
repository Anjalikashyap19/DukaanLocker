package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.NotificationRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escalation behavior: T-30 / T-15 / T-7 stage messages fire once each whenever
 * first seen (a missed calendar day is not a permanent skip), daily reminders
 * run from 6 days out through expiry day, an expired document nags daily for
 * expired-max-days and then once per expired-repeat-days window (never falling
 * permanently silent), and referenceId is the SHOP id so in-app taps navigate
 * correctly.
 *
 * The catch-up pass (expiredOnly=true) reports only already-expired documents,
 * so an upload that is expired on arrival surfaces within minutes at any hour,
 * while the daily countdown never fires overnight.
 */
@SpringBootTest
@Transactional
@DisplayName("DocumentExpiryScheduler escalation tests")
class DocumentExpirySchedulerTest {

    @Autowired private DocumentExpiryScheduler scheduler;
    @Autowired private UserRepository userRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;

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
        shop.setShopName("Diya Biriyani");
        shop.setOwnerName("Test Owner");
        shop.setMobile("9876543210");
        shop.setCategory("RESTAURANT");
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

    private Document createDoc(long daysUntilExpiry) {
        return createDoc(DocumentType.FSSAI_FOOD_LICENSE, daysUntilExpiry);
    }

    private Document createDoc(DocumentType type, long daysUntilExpiry) {
        Document doc = new Document(shop, type);
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setFileName(type.name().toLowerCase() + ".pdf");
        doc.setFileUrl("documents/1111_1/" + type.name().toLowerCase() + "/" + type.name().toLowerCase() + ".pdf");
        if (daysUntilExpiry >= 0) {
            doc.setExpiryDate(LocalDate.now().plusDays(daysUntilExpiry).atStartOfDay());
        } else {
            doc.setExpiryDate(LocalDate.now().minusDays(-daysUntilExpiry).atStartOfDay());
        }
        return documentRepository.save(doc);
    }

    private List<Notification> run() {
        scheduler.checkExpiringDocuments();
        return notificationRepository.findAll();
    }

    private List<Notification> runCatchUp() {
        scheduler.checkExpiringDocuments(true);
        return notificationRepository.findAll();
    }

    @Test
    @DisplayName("First sighting at 25 days out fires the T30 stage with shop as referenceId")
    void t30StageFiresWhenFirstSeen() {
        Document doc = createDoc(25);

        List<Notification> notifs = run();

        assertThat(notifs).hasSize(1);
        Notification n = notifs.get(0);
        assertThat(n.getType()).isEqualTo("EXPIRING_SOON");
        assertThat(n.getCategory()).isEqualTo("ALERT");
        assertThat(n.getReferenceId()).isEqualTo(shop.getId());          // navigation fix
        assertThat(n.getMetadata()).isEqualTo("FSSAI_FOOD_LICENSE");
        assertThat(n.getDedupeKey()).isEqualTo("expiry:" + doc.getId() + ":T30");
        assertThat(n.getBody()).contains("Diya Biriyani")
                .contains(LocalDate.now().plusDays(25).getYear() + "");
    }

    @Test
    @DisplayName("Stage messages fire only once even if many runs happen the same day")
    void stageFiresOnlyOnce() {
        createDoc(25);

        run();
        run();
        run();

        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("10 days out -> T15 stage (T30 is in the past, not a permanent skip)")
    void t15StageWhenFirstSeenAt10Days() {
        Document doc = createDoc(10);

        List<Notification> notifs = run();

        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getDedupeKey()).isEqualTo("expiry:" + doc.getId() + ":T15");
        assertThat(notifs.get(0).getBody()).containsIgnoringCase("renew");
    }

    @Test
    @DisplayName("Exactly 7 days out -> T7 stage with the reassuring copy")
    void t7StageAtExactlySevenDays() {
        Document doc = createDoc(7);

        List<Notification> notifs = run();

        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getDedupeKey()).isEqualTo("expiry:" + doc.getId() + ":T7");
        assertThat(notifs.get(0).getBody()).containsIgnoringCase("don't worry");
        assertThat(notifs.get(0).getType()).isEqualTo("EXPIRING_SOON");
    }

    @Test
    @DisplayName("Daily escalation: one reminder per remaining day, never twice in one day")
    void dailyEscalationCountsDown() {
        Document doc = createDoc(5);

        assertThat(run()).hasSize(1);
        assertThat(run()).hasSize(1);                       // same day, no double
        assertThat(run().get(0).getDedupeKey()).isEqualTo("expiry:" + doc.getId() + ":D5");

        // next day: expiry moves one day closer -> a fresh reminder
        doc.setExpiryDate(LocalDate.now().plusDays(4).atStartOfDay());
        documentRepository.save(doc);
        documentRepository.flush();

        List<Notification> notifs = run();
        assertThat(notifs).hasSize(2);
        assertThat(notifs)
                .anyMatch(n -> ("expiry:" + doc.getId() + ":D4").equals(n.getDedupeKey()));
    }

    @Test
    @DisplayName("Expiry day uses the expires-today copy")
    void expiryDayCopy() {
        createDoc(0);

        List<Notification> notifs = run();

        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getTitle()).containsIgnoringCase("expires today");
        assertThat(notifs.get(0).getBody()).containsIgnoringCase("TODAY");
    }

    @Test
    @DisplayName("Expired docs nag daily up to the 7-day cap, then stop; status flips to EXPIRED")
    void expiredNagRespectsCapAndSetsStatus() {
        Document doc = createDoc(-3);

        List<Notification> notifs = run();

        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getType()).isEqualTo("EXPIRED");
        assertThat(notifs.get(0).getCategory()).isEqualTo("ALERT");
        assertThat(notifs.get(0).getDedupeKey()).isEqualTo("expiry:" + doc.getId() + ":X3");
        assertThat(documentRepository.findById(doc.getId())).hasValueSatisfying(
                d -> assertThat(d.getStatus()).isEqualTo(DocumentStatus.EXPIRED));
    }

    @Test
    @DisplayName("Past the daily cap the first repeat window still notifies (10 days out)")
    void expiredJustPastCapSendsFirstRepeat() {
        Document doc = createDoc(-10);

        List<Notification> notifs = run();

        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getType()).isEqualTo("EXPIRED");
        assertThat(notifs.get(0).getDedupeKey()).isEqualTo("expiry:" + doc.getId() + ":XR0");
    }

    @Test
    @DisplayName("Repeat window: every run inside the same window is suppressed")
    void expiredRepeatWindowFiresOnce() {
        Document doc = createDoc(-10);

        assertThat(run()).hasSize(1);
        assertThat(run()).hasSize(1);
        assertThat(run()).hasSize(1);

        assertThat(notificationRepository.findAll())
                .allMatch(n -> ("expiry:" + doc.getId() + ":XR0").equals(n.getDedupeKey()));
    }

    @Test
    @DisplayName("A much older document lands in a later repeat window and notifies again")
    void longExpiredDocUsesLaterRepeatWindow() {
        Document doc = createDoc(-30);

        List<Notification> notifs = run();

        // (30 - 7 - 1) / 14 = 1 -> second repeat window
        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getDedupeKey()).isEqualTo("expiry:" + doc.getId() + ":XR1");
        assertThat(notifs.get(0).getBody()).contains("30");
    }

    @Test
    @DisplayName("A suppressed long-expired document is still marked EXPIRED")
    void suppressedExpiredDocStillGetsExpiredStatus() {
        Document doc = createDoc(-10);

        run();                                   // first repeat fires
        run();                                   // suppressed by the dedupe key

        assertThat(documentRepository.findById(doc.getId())).hasValueSatisfying(
                d -> assertThat(d.getStatus()).isEqualTo(DocumentStatus.EXPIRED));
    }

    @Test
    @DisplayName("Runs with no open session (as the cron does) without lazy-proxy failures")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void runsOutsideAnyTransaction() {
        createDoc(-27);
        createDoc(DocumentType.GST, 25);

        // Document.shop is LAZY, so a plain findAll() here would hand back
        // uninitializable proxies and blow up on doc.getShop().getOwner().
        // This is the guard for the bug that stopped every expiry notification
        // from ever being sent in production.
        List<Notification> notifs = runCatchUp();

        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getType()).isEqualTo("EXPIRED");
        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Catch-up pass reports an already-expired document outside waking hours")
    void catchUpPassNotifiesExpiredDoc() {
        Document doc = createDoc(-27);

        List<Notification> notifs = runCatchUp();

        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getType()).isEqualTo("EXPIRED");
        assertThat(notifs.get(0).getCategory()).isEqualTo("ALERT");
        assertThat(notifs.get(0).getReferenceId()).isEqualTo(shop.getId());
        assertThat(notifs.get(0).getDedupeKey()).isEqualTo("expiry:" + doc.getId() + ":XR1");
        assertThat(documentRepository.findById(doc.getId())).hasValueSatisfying(
                d -> assertThat(d.getStatus()).isEqualTo(DocumentStatus.EXPIRED));
    }

    @Test
    @DisplayName("Catch-up pass stays silent for documents that have not expired yet")
    void catchUpPassIgnoresUpcomingDocs() {
        createDoc(DocumentType.FSSAI_FOOD_LICENSE, 25);
        createDoc(DocumentType.GST, 5);
        createDoc(DocumentType.PAN, 0);

        assertThat(runCatchUp()).isEmpty();
    }

    @Test
    @DisplayName("Catch-up pass does not double-send what the main pass already sent")
    void catchUpSharesDedupeKeysWithMainPass() {
        createDoc(-27);

        assertThat(run()).hasSize(1);
        assertThat(runCatchUp()).hasSize(1);        // same XR key, still just the one
    }

    @Test
    @DisplayName("Documents further out than 30 days are ignored")
    void farFutureIgnored() {
        createDoc(60);

        assertThat(run()).isEmpty();
    }

    @Test
    @DisplayName("Documents without an expiry date are ignored")
    void missingExpiryDateIgnored() {
        Document doc = new Document(shop, DocumentType.PAN);
        doc.setStatus(DocumentStatus.UPLOADED);
        documentRepository.save(doc);

        assertThat(run()).isEmpty();
    }

    @Test
    @DisplayName("Not-uploaded placeholder rows are ignored")
    void notUploadedIgnored() {
        Document doc = new Document(shop, DocumentType.FSSAI_FOOD_LICENSE);
        doc.setStatus(DocumentStatus.NOT_UPLOADED);
        doc.setExpiryDate(LocalDate.now().plusDays(5).atStartOfDay());
        documentRepository.save(doc);

        assertThat(run()).isEmpty();
    }

    @Test
    @DisplayName("GST is permanent: a stale expiry date never triggers an alert")
    void gstNeverNotifiedEvenIfExpiryDatePresent() {
        // Legacy row written before GST was treated as permanent.
        Document doc = createDoc(DocumentType.GST, -500);
        doc.setStatus(DocumentStatus.EXPIRED);
        documentRepository.save(doc);

        assertThat(run()).isEmpty();
        assertThat(runCatchUp()).isEmpty();
    }

    @Test
    @DisplayName("GST is permanent: it is not flipped to EXPIRED either")
    void gstStatusNotSyncedToExpired() {
        Document doc = createDoc(DocumentType.GST, -500);
        documentRepository.saveAndFlush(doc);

        scheduler.checkExpiringDocuments();

        // Status is untouched by the scheduler - nothing left it as expired.
        assertThat(documentRepository.findById(doc.getId()).orElseThrow().getStatus())
                .isNotEqualTo(DocumentStatus.EXPIRED);
    }
}
