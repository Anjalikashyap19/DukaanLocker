package com.shoplocker.fssai.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.shoplocker.fssai.entity.DeviceToken;
import com.shoplocker.fssai.entity.Notification;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.DeviceTokenRepository;
import com.shoplocker.fssai.repository.NotificationRepository;
import com.shoplocker.fssai.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Value("${firebase.credentials.path:}")
    private String firebaseCredentialsPath;

    private boolean firebaseInitialized = false;

    public NotificationService(NotificationRepository notificationRepository,
                               DeviceTokenRepository deviceTokenRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void initFirebase() {
        try {
            if (firebaseCredentialsPath == null || firebaseCredentialsPath.isBlank()) {
                log.warn("Firebase credentials path not set. Push notifications will be disabled.");
                return;
            }
            InputStream serviceAccount = new FileInputStream(firebaseCredentialsPath);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            firebaseInitialized = true;
            log.info("Firebase initialized successfully for push notifications");
        } catch (IOException e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }

    @Transactional
    public Notification createNotification(Long userId, String title, String body, String type, Long referenceId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found for notification: {}", userId);
            return null;
        }
        User user = userOpt.get();
        Notification notification = new Notification(user, title, body, type, referenceId);
        notificationRepository.save(notification);
        log.info("Notification created for user {}: {}", userId, title);
        return notification;
    }

    @Transactional
    public void sendPushNotification(Long userId, String title, String body, Map<String, String> data) {
        if (!firebaseInitialized) {
            log.debug("Firebase not initialized. Skipping push notification.");
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("No device tokens for user {}. Skipping push.", userId);
            return;
        }

        for (DeviceToken deviceToken : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(com.google.firebase.messaging.Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putAllData(data != null ? data : Map.of())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setTtl(86400000L)
                                .build())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.debug("Push notification sent to token {}: {}", deviceToken.getToken().substring(0, 10) + "...", response);
            } catch (FirebaseMessagingException e) {
                log.error("Failed to send push notification: {}", e.getMessage());
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    log.info("Removing invalid token for user {}", userId);
                    deviceTokenRepository.delete(deviceToken);
                }
            }
        }
    }

    @Transactional
    public void sendPushAndCreateNotification(Long userId, String title, String body, String type, Long referenceId, Map<String, String> data) {
        createNotification(userId, title, body, type, referenceId);
        sendPushNotification(userId, title, body, data);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void saveDeviceToken(Long userId, String token, String platform) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return;

        Optional<DeviceToken> existing = deviceTokenRepository.findByToken(token);
        if (existing.isPresent()) {
            existing.get().setLastUsedAt(LocalDateTime.now());
            deviceTokenRepository.save(existing.get());
        } else {
            DeviceToken deviceToken = new DeviceToken(userOpt.get(), token, platform);
            deviceTokenRepository.save(deviceToken);
        }
        log.info("Device token saved for user {}", userId);
    }

    @Transactional
    public void removeDeviceToken(String token) {
        deviceTokenRepository.findByToken(token).ifPresent(deviceTokenRepository::delete);
    }
}
