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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Drip-feeds missing-document reminders: 3-5 per owner per day, one per
 * configured time slot (with per-owner-per-day jitter so timings shift daily),
 * each about a different missing document with rotating copy.
 *
 * Every send carries a per-slot dedupe key (missing:{date}:slot:{idx}) so a
 * slot fires exactly once even with repeated cron runs / logins; per-document
 * eligibility uses a calendar-day window so tomorrow rotates to other docs.
 */
@Component
public class DocumentMissingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentMissingScheduler.class);

    private static final String TYPE = "MISSING_DOCUMENT";
    private static final String CATEGORY = "MISSING_DOC";

    private final DocumentRepository documentRepository;
    private final ShopRepository shopRepository;
    private final RequiredDocumentService requiredDocumentService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    /** Comma-separated local times, e.g. 10:00,13:30,17:00,20:00 */
    @Value("${notification.missing-doc.slots:10:00,13:30,17:00,20:00}")
    private String slotsConfig;

    /** +/- minutes of deterministic per-owner-per-day jitter around each slot. */
    @Value("${notification.missing-doc.jitter-minutes:45}")
    private int jitterMinutes;

    @Value("${notification.missing-doc.min-per-day:3}")
    private int minPerDay;

    @Value("${notification.missing-doc.max-per-day:5}")
    private int maxPerDay;

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

    /** Cron entry point: serves every due, unserved slot for every owner. */
    public void checkMissingDocuments() {
        log.info("Running missing document check scheduler...");
        List<Shop> allShops = shopRepository.findAll();
        Map<Long, List<Shop>> byOwner = allShops.stream()
                .collect(Collectors.groupingBy(s -> s.getOwner().getId()));

        int notificationsSent = 0;
        for (Map.Entry<Long, List<Shop>> entry : byOwner.entrySet()) {
            try {
                notificationsSent += serveDueSlots(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("Missing-document check failed for owner {}: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }
        log.info("Missing document check completed. Sent {} notifications.", notificationsSent);
    }

    /** Login-time trigger: catches this owner up on any slots already due today. */
    public int checkMissingDocumentsForOwner(Long ownerId) {
        List<Shop> shops = shopRepository.findByOwnerId(ownerId);
        if (shops.isEmpty()) return 0;
        try {
            return serveDueSlots(ownerId, shops);
        } catch (Exception e) {
            log.error("Missing-document check failed for owner {}: {}", ownerId, e.getMessage(), e);
            return 0;
        }
    }

    private int serveDueSlots(Long ownerId, List<Shop> shops) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        String dateKey = today.toString();
        ZoneId zone = ZoneId.systemDefault();
        java.time.Instant startOfDay = today.atStartOfDay(zone).toInstant();

        List<LocalTime> slots = parseSlots();
        if (slots.isEmpty()) return 0;

        int target = dailyTarget(ownerId, today);
        List<Integer> chosenSlots = chosenSlots(ownerId, today, slots.size(), target);

        int sentToday = (int) notificationRepository.countByUserIdAndCategoryAndCreatedAtAfter(
                ownerId, CATEGORY, startOfDay);

        int sent = 0;
        NotificationCopy.Period period = NotificationCopy.periodFor(now);
        int variantSeed = NotificationCopy.variant(ownerId, today, Integer.MAX_VALUE);

        for (int slotIndex : chosenSlots) {
            if (sentToday + sent >= target) break;

            LocalTime planned = slots.get(slotIndex)
                    .plusMinutes(jitterMinutes(ownerId, dateKey, slotIndex));
            if (now.isBefore(planned)) continue;                     // slot not due yet

            String slotKey = "missing:" + dateKey + ":slot:" + slotIndex;
            if (notificationRepository.existsByUserIdAndDedupeKey(ownerId, slotKey)) continue;

            Candidate candidate = pickCandidate(ownerId, shops, today, startOfDay);
            if (candidate == null) continue;                          // nothing eligible right now

            String title = NotificationCopy.missingDocTitle(candidate.documentType);
            String body = NotificationCopy.missingDocBody(
                    candidate.documentType, period, candidate.shop.getShopName(), variantSeed + slotIndex);

            boolean created = notificationService.sendPushAndCreateNotification(
                    ownerId, title, body, TYPE, candidate.shop.getId(),
                    Map.of("shopId", candidate.shop.getId().toString(),
                           "documentType", candidate.documentType.name()),
                    slotKey
            );
            if (created) sent++;
        }
        return sent;
    }

    // ── Candidate selection ────────────────────────────────────────────────

    private record Candidate(Shop shop, DocumentType documentType) {}

    private Candidate pickCandidate(Long ownerId, List<Shop> shops, LocalDate today, java.time.Instant startOfDay) {
        List<Candidate> candidates = new ArrayList<>();
        for (Shop shop : shops) {
            Set<DocumentType> requiredDocs = requiredDocumentService.getRequiredDocuments(
                    shop.getCategory(), shop.getScale());

            Set<DocumentType> uploadedTypes = new HashSet<>();
            for (Document doc : documentRepository.findByShopId(shop.getId())) {
                if (doc.getStatus() != DocumentStatus.NOT_UPLOADED) {
                    uploadedTypes.add(doc.getDocumentType());
                }
            }

            for (DocumentType docType : requiredDocs) {
                if (uploadedTypes.contains(docType)) continue;
                // Calendar-day gate: already reminded about this doc for this shop today
                if (notificationRepository
                        .existsByUserIdAndTypeAndReferenceIdAndMetadataAndCreatedAtAfter(
                                ownerId, TYPE, shop.getId(), docType.name(), startOfDay)) {
                    continue;
                }
                candidates.add(new Candidate(shop, docType));
            }
        }
        if (candidates.isEmpty()) return null;

        // Day-of-year rotation so a new day starts at a different doc
        long dayOffset = ChronoUnit.DAYS.between(LocalDate.of(1970, 1, 1), today);
        int offset = (int) Math.floorMod(dayOffset, candidates.size());
        return candidates.get(offset);
    }

    // ── Scheduling helpers (all deterministic per owner+day) ───────────────

    private List<LocalTime> parseSlots() {
        List<LocalTime> slots = new ArrayList<>();
        for (String raw : slotsConfig.split(",")) {
            String value = raw.trim();
            if (value.isEmpty()) continue;
            try {
                slots.add(LocalTime.parse(value));
            } catch (DateTimeParseException e) {
                log.warn("Ignoring unparsable missing-doc slot '{}'", value);
            }
        }
        slots.sort(Comparator.naturalOrder());
        return slots;
    }

    private int dailyTarget(Long ownerId, LocalDate today) {
        int min = Math.max(0, Math.min(minPerDay, maxPerDay));
        int max = Math.max(min, maxPerDay);
        if (min == max) return min;
        int h = Objects.hash(ownerId, today.toString());
        return min + Math.floorMod(h, (max - min + 1));
    }

    /** Deterministically picks which of today's slots will actually send. */
    private List<Integer> chosenSlots(Long ownerId, LocalDate today, int slotCount, int target) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) indices.add(i);
        Collections.shuffle(indices, new Random(Objects.hash(ownerId, today.toString(), "slots")));
        return indices.stream()
                .limit(Math.min(target, slotCount))
                .sorted()
                .collect(Collectors.toList());
    }

    private long jitterMinutes(Long ownerId, String dateKey, int slotIndex) {
        if (jitterMinutes <= 0) return 0;
        int h = Objects.hash(ownerId, dateKey, slotIndex);
        return Math.floorMod(h, (2 * jitterMinutes + 1)) - (long) jitterMinutes;
    }
}
