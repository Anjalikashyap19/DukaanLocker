package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.RenewalOrderResponse;
import com.shoplocker.fssai.entity.Document;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.RenewalOrder;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.RenewalOrderRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.util.DlIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renewal request flow (payment deferred): the user asks DukaanLocker to renew
 * an expiring/expired document; ops fulfills the order and uploads the
 * certificate into the user's DL-ID locker folder.
 */
@Service
public class RenewalOrderService {

    private static final Logger log = LoggerFactory.getLogger(RenewalOrderService.class);

    private static final Set<String> VALID_STATUSES = Set.of(
            RenewalOrder.STATUS_REQUESTED,
            RenewalOrder.STATUS_IN_PROGRESS,
            RenewalOrder.STATUS_COMPLETED,
            RenewalOrder.STATUS_CANCELLED);

    private static final Set<String> OPEN_STATUSES = Set.of(
            RenewalOrder.STATUS_REQUESTED,
            RenewalOrder.STATUS_IN_PROGRESS);

    private final RenewalOrderRepository renewalOrderRepository;
    private final ShopRepository shopRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public RenewalOrderService(RenewalOrderRepository renewalOrderRepository,
                               ShopRepository shopRepository,
                               DocumentRepository documentRepository,
                               UserRepository userRepository,
                               NotificationService notificationService) {
        this.renewalOrderRepository = renewalOrderRepository;
        this.shopRepository = shopRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public RenewalOrderResponse requestRenewal(User user, Long shopId, DocumentType documentType, String notes) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new FssaiException("Shop not found", FailureCode.SHOP_NOT_FOUND));

        // Idempotent: an already-open request for the same doc returns as-is
        List<RenewalOrder> open = renewalOrderRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(o -> o.getShop().getId().equals(shopId))
                .filter(o -> o.getDocumentType() == documentType)
                .filter(o -> OPEN_STATUSES.contains(o.getStatus()))
                .toList();
        if (!open.isEmpty()) {
            return toResponse(open.get(0));
        }

        String dlId = ensureDlId(user);
        Long documentId = documentRepository.findByShopIdAndDocumentType(shopId, documentType)
                .map(Document::getId)
                .orElse(null);

        RenewalOrder order = new RenewalOrder(user, shop, documentType, documentId, dlId);
        order.setNotes(notes);
        order = renewalOrderRepository.save(order);

        log.info("Renewal requested: order {} user {} shop {} {} dlId={}",
                order.getId(), user.getId(), shopId, documentType, dlId);

        String docName = com.shoplocker.fssai.scheduler.NotificationCopy.formatDocumentName(documentType);
        notificationService.sendPushAndCreateNotification(
                user.getId(),
                "Renewal request received",
                String.format("We've got it! Your %s renewal for %s is in progress - it will be uploaded to your Dukaan Locker within 24 hours.",
                        docName, shop.getShopName()),
                "RENEWAL_REQUESTED",
                shopId,
                Map.of("shopId", String.valueOf(shopId),
                       "documentType", documentType.name(),
                       "renewalId", String.valueOf(order.getId()),
                       "route", "renewal"),
                "renewal:" + order.getId() + ":requested"
        );

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<RenewalOrderResponse> listForUser(Long userId) {
        return renewalOrderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RenewalOrderResponse> listAll() {
        return renewalOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RenewalOrderResponse updateStatus(Long orderId, String status, String notes) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new FssaiException("Invalid status: " + status, FailureCode.INVALID_REQUEST);
        }
        RenewalOrder order = renewalOrderRepository.findById(orderId)
                .orElseThrow(() -> new FssaiException("Renewal order not found", FailureCode.NOT_FOUND));

        boolean justCompleted = RenewalOrder.STATUS_COMPLETED.equals(status)
                && !RenewalOrder.STATUS_COMPLETED.equals(order.getStatus());

        order.setStatus(status);
        if (notes != null && !notes.isBlank()) order.setNotes(notes);
        if (justCompleted) order.setCompletedAt(Instant.now());
        renewalOrderRepository.save(order);

        if (justCompleted) {
            String docName = com.shoplocker.fssai.scheduler.NotificationCopy.formatDocumentName(order.getDocumentType());
            notificationService.sendPushAndCreateNotification(
                    order.getUser().getId(),
                    docName + " uploaded",
                    String.format("Your renewed %s for %s is now in your Dukaan Locker. Open your locker to view it.",
                            docName, order.getShop().getShopName()),
                    "RENEWAL_COMPLETED",
                    order.getShop().getId(),
                    Map.of("shopId", String.valueOf(order.getShop().getId()),
                           "documentType", order.getDocumentType().name(),
                           "renewalId", String.valueOf(order.getId()),
                           "route", "locker"),
                    "renewal:" + order.getId() + ":completed"
            );
        }
        return toResponse(order);
    }

    /** Returns the user's DL ID, assigning and persisting it on first use. */
    private String ensureDlId(User user) {
        if (DlIds.isMissing(user.getDlId())) {
            String dlId = DlIds.forUser(user.getId());
            user.setDlId(dlId);
            userRepository.save(user);
            return dlId;
        }
        return user.getDlId();
    }

    private RenewalOrderResponse toResponse(RenewalOrder order) {
        return new RenewalOrderResponse(
                order.getId(),
                order.getShop().getId(),
                order.getShop().getShopName(),
                order.getDocumentType().name(),
                order.getStatus(),
                order.getDlId(),
                order.getNotes(),
                order.getRequestedAt(),
                order.getCompletedAt());
    }
}
