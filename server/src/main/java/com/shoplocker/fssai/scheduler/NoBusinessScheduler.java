package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class NoBusinessScheduler {

    private static final Logger log = LoggerFactory.getLogger(NoBusinessScheduler.class);

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final NotificationService notificationService;

    public NoBusinessScheduler(UserRepository userRepository,
                                ShopRepository shopRepository,
                                NotificationService notificationService) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 10 * * ?")
    public void checkUsersWithoutBusiness() {
        log.info("Running no-business check scheduler...");

        List<User> allUsers = userRepository.findAll();
        int notificationsSent = 0;

        for (User user : allUsers) {
            // Only check ADMIN users (managers don't create shops)
            if (user.getRole() != Role.ADMIN) continue;

            // Skip users registered less than 2 days ago
            if (user.getCreatedAt() == null) continue;
            long daysSinceRegistration = ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now());
            if (daysSinceRegistration < 2) continue;

            // Check if user has any shops
            boolean hasShops = !shopRepository.findByOwnerId(user.getId()).isEmpty();
            if (hasShops) continue;

            // Check if we already sent this notification today
            boolean alreadyNotified = notificationService.getNotifications(user.getId()).stream()
                    .filter(n -> "NO_BUSINESS".equals(n.getType()))
                    .filter(n -> n.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                    .findFirst()
                    .isPresent();

            if (alreadyNotified) continue;

            // Also check if we sent it in the last 7 days (don't spam)
            boolean recentlyNotified = notificationService.getNotifications(user.getId()).stream()
                    .filter(n -> "NO_BUSINESS".equals(n.getType()))
                    .filter(n -> ChronoUnit.DAYS.between(n.getCreatedAt().toLocalDate(), LocalDate.now()) <= 7)
                    .findFirst()
                    .isPresent();

            if (recentlyNotified) continue;

            String title = "\uD83D\uDE80 Ready to get started?";
            String body = String.format("Hey %s! Add your first business to unlock all features. It only takes 2 minutes!",
                    user.getUserName() != null ? user.getUserName() : "there");

            notificationService.sendPushAndCreateNotification(
                    user.getId(), title, body, "NO_BUSINESS", null,
                    Map.of("action", "add_business")
            );
            notificationsSent++;
        }

        log.info("No-business check completed. Sent {} notifications.", notificationsSent);
    }
}
