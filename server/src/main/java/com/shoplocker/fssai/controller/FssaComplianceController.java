package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.service.ShopAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fssa")
@Tag(name = "FSSAI Compliance", description = "FSSAI annual return filing compliance")
public class FssaComplianceController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int FINE_PER_DAY = 100;
    private static final int SERVICE_CHARGE = 2000;

    private final ShopRepository shopRepository;
    private final ShopAccessService shopAccessService;

    public FssaComplianceController(ShopRepository shopRepository,
                                    ShopAccessService shopAccessService) {
        this.shopRepository = shopRepository;
        this.shopAccessService = shopAccessService;
    }

    /**
     * Returns FSSAI compliance status for all shops owned by the authenticated user.
     */
    @GetMapping("/compliance")
    @Operation(summary = "Get FSSAI compliance status for all shops")
    public ResponseEntity<List<ComplianceStatus>> getCompliance(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        List<Shop> shops = shopRepository.findByOwnerId(user.getId());
        LocalDate today = LocalDate.now(IST);
        LocalDate deadline = LocalDate.of(today.getYear(), 5, 31);

        List<ComplianceStatus> result = new ArrayList<>();
        for (Shop shop : shops) {
            if (!Boolean.TRUE.equals(shop.getIsFssaiApplicable())) continue;

            boolean filed = Boolean.TRUE.equals(shop.getFssaReturnFiled());
            long overdueDays = 0;
            int fine = 0;
            int totalPayable = 0;
            String status = "pending";

            if (filed) {
                status = "filed";
            } else if (today.isAfter(deadline)) {
                overdueDays = ChronoUnit.DAYS.between(deadline, today);
                fine = (int) (overdueDays * FINE_PER_DAY);
                totalPayable = fine + SERVICE_CHARGE;
                status = "overdue";
            } else if (!today.isBefore(LocalDate.of(today.getYear(), 1, 1))) {
                long daysLeft = ChronoUnit.DAYS.between(today, deadline) + 1;
                status = daysLeft <= 15 ? "due_soon" : "pending";
            }

            result.add(new ComplianceStatus(
                    shop.getId(),
                    shop.getShopName(),
                    shop.getBranchName(),
                    filed,
                    shop.getFssaReturnFiledDate(),
                    overdueDays,
                    fine,
                    SERVICE_CHARGE,
                    totalPayable,
                    status,
                    deadline.toString()
            ));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Marks the FSSAI annual return as filed for a shop. Stops further notifications.
     */
    @PostMapping("/{shopId}/mark-filed")
    @Operation(summary = "Mark FSSAI annual return as filed")
    @Transactional
    public ResponseEntity<Map<String, String>> markFiled(@PathVariable Long shopId,
                                                          Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        Shop shop = shopRepository.findByIdAndOwnerId(shopId, user.getId())
                .orElseThrow(() -> new RuntimeException("Shop not found"));

        if (!Boolean.TRUE.equals(shop.getIsFssaiApplicable())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "FSSAI is not applicable for this shop"));
        }

        shop.setFssaReturnFiled(true);
        shop.setFssaReturnFiledDate(LocalDate.now(IST));
        shopRepository.save(shop);

        return ResponseEntity.ok(Map.of("message", "FSSAI return marked as filed"));
    }

    public record ComplianceStatus(
            Long shopId,
            String shopName,
            String branchName,
            boolean filed,
            LocalDate filedDate,
            long overdueDays,
            int fine,
            int serviceCharge,
            int totalPayable,
            String status,
            String deadline
    ) {}
}
