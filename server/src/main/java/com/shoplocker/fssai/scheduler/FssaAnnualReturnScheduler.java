package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class FssaAnnualReturnScheduler {

    private static final Logger log = LoggerFactory.getLogger(FssaAnnualReturnScheduler.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String TYPE_REMINDER = "FSSAI_RETURN_REMINDER";
    private static final String TYPE_FINE = "FSSAI_RETURN_FINE";
    private static final int FINE_PER_DAY = 100;
    private static final int SERVICE_CHARGE = 2000;

    private final ShopRepository shopRepository;
    private final NotificationService notificationService;

    public FssaAnnualReturnScheduler(ShopRepository shopRepository,
                                     NotificationService notificationService) {
        this.shopRepository = shopRepository;
        this.notificationService = notificationService;
    }

    public void checkFssaAnnualReturn() {
        log.info("Running FSSAI annual return check scheduler...");
        LocalDate today = LocalDate.now(IST);
        LocalDate filingDeadline = LocalDate.of(today.getYear(), 5, 31);

        List<Shop> shops = shopRepository.findByIsFssaiApplicableTrueAndFssaReturnFiledFalse();
        int notificationsSent = 0;

        for (Shop shop : shops) {
            try {
                if (processShop(shop, today, filingDeadline)) {
                    notificationsSent++;
                }
            } catch (Exception e) {
                log.error("FSSAI check failed for shop {}: {}", shop.getId(), e.getMessage(), e);
            }
        }

        log.info("FSSAI annual return check completed. Sent {} notifications.", notificationsSent);
    }

    private boolean processShop(Shop shop, LocalDate today, LocalDate filingDeadline) {
        Long ownerId = shop.getOwner() != null ? shop.getOwner().getId() : null;
        if (ownerId == null) return false;

        if (!today.isBefore(LocalDate.of(today.getYear(), 1, 1)) && !today.isAfter(filingDeadline)) {
            // Filing window: Jan 1 - May 31, send reminder every 15 days
            int window = today.getDayOfYear() / 15;
            String dedupeKey = "fssa_reminder:" + shop.getId() + ":" + window;

            int daysLeft = (int) ChronoUnit.DAYS.between(today, filingDeadline) + 1;
            String title = "FSSAI Annual Return Filing Reminder";
            String body = String.format("Your FSSAI annual return for %s is due in %d days. Filing window closes on 31st May.",
                    shop.getShopName(), daysLeft);

            return notificationService.sendPushAndCreateNotification(
                    ownerId, title, body, TYPE_REMINDER, shop.getId(),
                    Map.of("shopId", shop.getId().toString(),
                           "route", "fssa_return",
                           "daysLeft", String.valueOf(daysLeft)),
                    dedupeKey
            );
        } else if (today.isAfter(filingDeadline)) {
            // Deadline crossed: send fine notification every day
            long overdueDays = ChronoUnit.DAYS.between(filingDeadline, today);
            int fine = (int) (overdueDays * FINE_PER_DAY);
            int totalPayable = fine + SERVICE_CHARGE;
            String dedupeKey = "fssa_fine:" + shop.getId() + ":" + today.toString();

            String title = "FSSAI Annual Return - Fine Started";
            String body = String.format("FSSAI annual return for %s is %d day(s) overdue. Fine: ₹%d/day = ₹%d + Service charge ₹2,000 = Total ₹%d.",
                    shop.getShopName(), overdueDays, FINE_PER_DAY, fine, totalPayable);

            return notificationService.sendPushAndCreateNotification(
                    ownerId, title, body, TYPE_FINE, shop.getId(),
                    Map.of("shopId", shop.getId().toString(),
                           "route", "fssa_return",
                           "daysOverdue", String.valueOf(overdueDays),
                           "fineAmount", String.valueOf(fine),
                           "totalPayable", String.valueOf(totalPayable),
                           "deadline", filingDeadline.toString()),
                    dedupeKey
            );
        }

        return false;
    }
}
