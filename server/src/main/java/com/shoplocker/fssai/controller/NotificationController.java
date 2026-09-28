package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.entity.Notification;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.NotificationService;
import com.shoplocker.fssai.service.ShopAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notification management")
public class NotificationController {

    private final NotificationService notificationService;
    private final ShopAccessService shopAccessService;

    public NotificationController(NotificationService notificationService,
                                   ShopAccessService shopAccessService) {
        this.notificationService = notificationService;
        this.shopAccessService = shopAccessService;
    }

    @GetMapping
    @Operation(summary = "Get all notifications for the current user")
    public ResponseEntity<List<Notification>> getNotifications(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        return ResponseEntity.ok(notificationService.getActiveNotifications(user.getId()));
    }

    @GetMapping("/bin")
    @Operation(summary = "Get notifications currently in the bin")
    public ResponseEntity<List<Notification>> getBinNotifications(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        return ResponseEntity.ok(notificationService.getBinNotifications(user.getId()));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(user.getId())));
    }

    @PutMapping("/mark-read")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    @PutMapping("/move-to-bin")
    @Operation(summary = "Move all notifications to bin (soft delete)")
    public ResponseEntity<?> moveToBin(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        notificationService.moveNotificationsToBin(user.getId());
        return ResponseEntity.ok(Map.of("message", "All notifications moved to bin"));
    }

    @PutMapping("/{id}/restore")
    @Operation(summary = "Restore a single notification from the bin")
    public ResponseEntity<?> restoreFromBin(@PathVariable Long id, Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        if (!notificationService.restoreFromBin(user.getId(), id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Notification not found in bin"));
        }
        return ResponseEntity.ok(Map.of("message", "Notification restored"));
    }

    @DeleteMapping("/permanent-delete")
    @Operation(summary = "Permanently delete all notifications from bin")
    public ResponseEntity<?> permanentDelete(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        notificationService.permanentDeleteNotifications(user.getId());
        return ResponseEntity.ok(Map.of("message", "All notifications permanently deleted"));
    }
}
