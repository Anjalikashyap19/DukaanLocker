package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Nudges owners whose onboarded account still has generated contact details. */
@Component
public class PendingContactReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingContactReminderScheduler.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public PendingContactReminderScheduler(UserRepository userRepository,
                                           NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public void sendReminders() {
        List<User> users = userRepository.findByContactEmailPendingTrueOrContactMobilePendingTrue();
        if (users.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String dedupeKey = "CONTACT_PENDING_" + now.format(DAY) + "_" + (now.getHour() / 6) * 6;
        int sent = 0;
        for (User user : users) {
            StringBuilder body = new StringBuilder("Your account still has temporary contact details. ");
            if (user.isContactEmailPending()) {
                body.append("Please verify and update your email address. ");
            }
            if (user.isContactMobilePending()) {
                body.append("Please verify and update your mobile number.");
            }
            boolean created = notificationService.sendPushAndCreateNotification(
                    user.getId(),
                    "Update your DukaanLocker account details",
                    body.toString().trim(),
                    "CONTACT_DETAILS_PENDING",
                    null,
                    Map.of("type", "CONTACT_DETAILS_PENDING"),
                    dedupeKey);
            if (created) {
                sent++;
            }
        }
        log.info("Pending-contact reminder pass: {} notification(s) sent to {} pending user(s)",
                sent, users.size());
    }
}
