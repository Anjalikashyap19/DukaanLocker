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

    @Value("${firebase.project-id:}")
    private String firebaseProjectId;

    private volatile boolean firebaseInitialized = false;

    public NotificationService(NotificationRepository notificationRepository,
                               DeviceTokenRepository deviceTokenRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void initFirebase() {
        if (firebaseCredentialsPath == null || firebaseCredentialsPath.isBlank()) {
            log.warn("FCM disabled: FIREBASE_CREDENTIALS_PATH/firebase.credentials.path is not configured");
            return;
        }

        try (InputStream serviceAccount = new FileInputStream(firebaseCredentialsPath)) {
            FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount));
            if (firebaseProjectId != null && !firebaseProjectId.isBlank()) {
                optionsBuilder.setProjectId(firebaseProjectId);
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(optionsBuilder.build());
            }
            firebaseInitialized = true;
            log.info("Firebase Admin initialized for FCM (projectId={})", firebaseProjectId);
        } catch (Exception e) {
            firebaseInitialized = false;
            log.error("FCM disabled: Firebase Admin initialization failed for credentials path {}: {}",
                    firebaseCredentialsPath, e.getMessage(), e);
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
            log.warn("Skipping FCM push for user {}: Firebase Admin is not initialized", userId);
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.warn("Skipping FCM push for user {}: no registered device tokens", userId);
            return;
        }

        log.info("Sending FCM push to user {} on {} device token(s), type={}", userId, tokens.size(),
                data == null ? "GENERAL" : data.getOrDefault("type", "GENERAL"));
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
                                .setNotification(AndroidNotification.builder()
                                        .setChannelId("dukaan_notifications_v2")
                                        .build())
                                .build())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("FCM push accepted for user {} and token {}...: messageId={}", userId,
                        tokenPrefix(deviceToken.getToken()), response);
            } catch (FirebaseMessagingException e) {
                log.error("FCM push failed for user {} and token {}...: code={}, message={}",
                        userId, tokenPrefix(deviceToken.getToken()), e.getMessagingErrorCode(), e.getMessage(), e);
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    log.info("Removing invalid FCM token for user {}", userId);
                    deviceTokenRepository.delete(deviceToken);
                }
            } catch (Exception e) {
                log.error("Unexpected FCM push failure for user {} and token {}...: {}",
                        userId, tokenPrefix(deviceToken.getToken()), e.getMessage(), e);
            }
        }
    }

    private static String tokenPrefix(String token) {
        if (token == null || token.isBlank()) return "empty";
        return token.substring(0, Math.min(10, token.length()));
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
        saveDeviceToken(userId, token, platform, false);
    }

    @Transactional
    public void saveDeviceToken(Long userId, String token, String platform, boolean sendWelcomePush) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return;

        Optional<DeviceToken> existing = deviceTokenRepository.findByToken(token);
        if (existing.isPresent()) {
            DeviceToken deviceToken = existing.get();
            // FCM tokens are device-install identifiers, not user identifiers.
            // Re-associate the token when another authenticated user logs in on
            // the same device instead of leaving it attached to the old user.
            deviceToken.setUser(userOpt.get());
            deviceToken.setPlatform(platform);
            deviceToken.setLastUsedAt(LocalDateTime.now());
            deviceTokenRepository.save(deviceToken);
            log.info("Existing FCM device token re-associated with user {}", userId);
        } else {
            DeviceToken deviceToken = new DeviceToken(userOpt.get(), token, platform);
            deviceTokenRepository.save(deviceToken);
            log.info("New FCM device token saved for user {}", userId);
        }

        if (sendWelcomePush) {
            sendPushNotification(userId,
                    "Welcome back!",
                    "You have successfully logged in to DukaanLocker.",
                    Map.of("type", "WELCOME"));
        }
    }

    @Transactional
    public void removeDeviceToken(String token) {
        deviceTokenRepository.findByToken(token).ifPresent(deviceTokenRepository::delete);
    }
}
