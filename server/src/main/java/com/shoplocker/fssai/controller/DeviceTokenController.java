package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.NotificationService;
import com.shoplocker.fssai.service.ShopAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/device-token")
@Tag(name = "Device Token", description = "FCM device token management")
public class DeviceTokenController {

    private final NotificationService notificationService;
    private final ShopAccessService shopAccessService;

    public DeviceTokenController(NotificationService notificationService,
                                  ShopAccessService shopAccessService) {
        this.notificationService = notificationService;
        this.shopAccessService = shopAccessService;
    }

    @PostMapping
    @Operation(summary = "Register or update FCM device token")
    public ResponseEntity<?> registerToken(@RequestBody Map<String, String> body, Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);

        String token = body.get("token");
        String platform = body.getOrDefault("platform", "ANDROID");

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token is required"));
        }

        notificationService.saveDeviceToken(user.getId(), token, platform);
        return ResponseEntity.ok(Map.of("message", "Token registered successfully"));
    }

    @DeleteMapping
    @Operation(summary = "Remove FCM device token on logout")
    public ResponseEntity<?> removeToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token != null) {
            notificationService.removeDeviceToken(token);
        }
        return ResponseEntity.ok(Map.of("message", "Token removed"));
    }
}
