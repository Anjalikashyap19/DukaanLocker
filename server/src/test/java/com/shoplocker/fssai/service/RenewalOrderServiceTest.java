package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.NotificationRepository;
import com.shoplocker.fssai.repository.RenewalOrderRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Request-first renewal flow: creates an order with the user's DL ID
 * (userId + 1110), is idempotent while an order is open, notifies the user
 * on request and again (once) when ops marks it COMPLETED, and rejects
 * invalid statuses.
 */
@SpringBootTest
@Transactional
@DisplayName("Renewal order service tests")
class RenewalOrderServiceTest {

    @Autowired private RenewalOrderService renewalOrderService;
    @Autowired private UserRepository userRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private RenewalOrderRepository renewalOrderRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User owner;
    private Shop shop;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        renewalOrderRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();

        owner = new User();
        owner.setUserName("Renewal Owner");
        owner.setEmailId("renewal@example.com");
        owner.setMobileNumber("9999999998");
        owner.setPassword("password");
        owner.setRole(Role.ADMIN);
        owner = userRepository.save(owner);

        shop = new Shop();
        shop.setShopName("Diya Biriyani");
        shop.setOwnerName("Renewal Owner");
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
        renewalOrderRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Request creates an order with DL ID = userId + 1110 and notifies the user")
    void requestCreatesOrderWithDlIdAndNotifies() {
        var response = renewalOrderService.requestRenewal(owner, shop.getId(), DocumentType.FSSAI_FOOD_LICENSE, null);

        String expectedDlId = String.format("%04d", owner.getId() + 1110);
        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.dlId()).isEqualTo(expectedDlId);
        assertThat(response.shopId()).isEqualTo(shop.getId());

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification n = notifications.get(0);
        assertThat(n.getType()).isEqualTo("RENEWAL_REQUESTED");
        assertThat(n.getReferenceId()).isEqualTo(shop.getId());
        assertThat(n.getDedupeKey()).isEqualTo("renewal:" + response.id() + ":requested");
    }

    @Test
    @DisplayName("Second request while one is open returns the same order without a duplicate notification")
    void secondRequestIsIdempotentWhileOpen() {
        var first = renewalOrderService.requestRenewal(owner, shop.getId(), DocumentType.FSSAI_FOOD_LICENSE, null);
        var second = renewalOrderService.requestRenewal(owner, shop.getId(), DocumentType.FSSAI_FOOD_LICENSE, "again");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Completing an order stamps completedAt and sends exactly one completion notification")
    void completingOrderNotifiesOnce() {
        var order = renewalOrderService.requestRenewal(owner, shop.getId(), DocumentType.FSSAI_FOOD_LICENSE, null);
        renewalOrderService.updateStatus(order.id(), "COMPLETED", "uploaded");

        var completed = renewalOrderService.listForUser(owner.getId()).get(0);
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.completedAt()).isNotNull();

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).extracting(Notification::getType)
                .containsExactlyInAnyOrder("RENEWAL_REQUESTED", "RENEWAL_COMPLETED");
        assertThat(notifications).extracting(Notification::getDedupeKey)
                .contains("renewal:" + order.id() + ":completed");

        // Re-issuing COMPLETED must not duplicate the completion notification
        renewalOrderService.updateStatus(order.id(), "COMPLETED", "again");
        assertThat(notificationRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Invalid status is rejected")
    void invalidStatusRejected() {
        var order = renewalOrderService.requestRenewal(owner, shop.getId(), DocumentType.FSSAI_FOOD_LICENSE, null);
        assertThatThrownBy(() -> renewalOrderService.updateStatus(order.id(), "BOGUS", null))
                .isInstanceOf(FssaiException.class);
    }

    @Test
    @DisplayName("DL ID persists on the user after the first request")
    void dlIdPersistedOnUser() {
        renewalOrderService.requestRenewal(owner, shop.getId(), DocumentType.FSSAI_FOOD_LICENSE, null);

        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getDlId()).isEqualTo(String.format("%04d", owner.getId() + 1110));
    }
}
