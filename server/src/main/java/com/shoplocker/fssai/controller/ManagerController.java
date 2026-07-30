package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.CreateManagerRequest;
import com.shoplocker.fssai.dto.ManagerResponse;
import com.shoplocker.fssai.dto.ShopResponse;
import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.ManagerShopAssignmentRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.security.JwtService;
import com.shoplocker.fssai.service.ShopAccessService;
import com.shoplocker.fssai.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manager management and shop assignment endpoints.
 * All endpoints except GET /api/managers/me/shops require ADMIN role.
 */
@RestController
@RequestMapping("/api/managers")
@Tag(name = "Managers", description = "Admin creates managers and assigns them to shops")
@SecurityRequirement(name = "bearer-jwt")
public class ManagerController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShopService shopService;
    private final ShopAccessService shopAccessService;
    private final ManagerShopAssignmentRepository assignmentRepository;

    public ManagerController(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             ShopService shopService,
                             ShopAccessService shopAccessService,
                             ManagerShopAssignmentRepository assignmentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.shopService = shopService;
        this.shopAccessService = shopAccessService;
        this.assignmentRepository = assignmentRepository;
    }

    @Operation(summary = "Create a manager", description = "ADMIN only. Creates a new user with role=MANAGER. " +
            "The manager is linked to the creating admin via createdByAdmin.")
    @PostMapping
    public ResponseEntity<ManagerResponse> createManager(@Valid @RequestBody CreateManagerRequest request,
                                                          Authentication authentication) {
        User admin = shopAccessService.getAuthenticatedUser(authentication);
        shopAccessService.ensureAdmin(admin);

        String email = request.getEmailId().trim().toLowerCase();
        String mobile = request.getMobileNumber().trim();

        if (userRepository.existsByEmailId(email)) {
            throw new FssaiException("A user already exists with this email address",
                    FailureCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByMobileNumber(mobile)) {
            throw new FssaiException("A user already exists with this mobile number",
                    FailureCode.DUPLICATE_MOBILE);
        }

        User manager = new User();
        manager.setUserName(request.getUserName().trim());
        manager.setMobileNumber(mobile);
        manager.setEmailId(email);
        manager.setPassword(passwordEncoder.encode(request.getPassword()));
        manager.setRole(Role.MANAGER);
        manager.setEnabled(true);
        manager.setCreatedByAdmin(admin);

        User saved = userRepository.save(manager);

        ManagerResponse response = new ManagerResponse(
                saved.getId(),
                saved.getUserName(),
                saved.getMobileNumber(),
                saved.getEmailId(),
                saved.getRole(),
                saved.isEnabled(),
                admin.getId(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List managers", description = "ADMIN only. Returns managers created by the logged-in admin.")
    @GetMapping
    public ResponseEntity<List<ManagerResponse>> getManagers(Authentication authentication) {
        User admin = shopAccessService.getAuthenticatedUser(authentication);
        shopAccessService.ensureAdmin(admin);

        List<User> managers = userRepository.findByCreatedByAdminIdAndRole(admin.getId(), Role.MANAGER);

        List<ManagerResponse> responses = managers.stream().map(m -> new ManagerResponse(
                m.getId(), m.getUserName(), m.getMobileNumber(), m.getEmailId(),
                m.getRole(), m.isEnabled(),
                m.getCreatedByAdmin() != null ? m.getCreatedByAdmin().getId() : null,
                m.getCreatedAt(), m.getUpdatedAt()
        )).toList();

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Assign manager to shop", description = "ADMIN only. Assigns a manager to a shop " +
            "owned by the admin. Prevents duplicate assignments.")
    @PostMapping("/{managerId}/shops/{shopId}")
    public ResponseEntity<ManagerResponse> assignShopToManager(@PathVariable Long managerId,
                                                                @PathVariable Long shopId,
                                                                Authentication authentication) {
        User admin = shopAccessService.getAuthenticatedUser(authentication);
        shopAccessService.ensureAdmin(admin);

        // Verify manager belongs to this admin
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new FssaiException("Manager not found", FailureCode.MANAGER_NOT_FOUND));

        if (manager.getRole() != Role.MANAGER) {
            throw new FssaiException("User is not a manager", FailureCode.INVALID_REQUEST);
        }

        if (manager.getCreatedByAdmin() == null || !manager.getCreatedByAdmin().getId().equals(admin.getId())) {
            throw new FssaiException("You do not have permission to assign this manager",
                    FailureCode.FORBIDDEN);
        }

        // Verify shop belongs to admin
        shopAccessService.validateShopOwner(admin, shopId);

        // Prevent duplicate
        if (assignmentRepository.existsByManagerIdAndShopIdAndActiveTrue(managerId, shopId)) {
            throw new FssaiException("Manager is already assigned to this shop",
                    FailureCode.DUPLICATE_ASSIGNMENT);
        }

        com.shoplocker.fssai.entity.Shop shop = shopAccessService.getOwnedShop(shopId, admin);

        ManagerShopAssignment assignment = new ManagerShopAssignment(manager, shop, admin);
        assignmentRepository.save(assignment);

        ManagerResponse response = new ManagerResponse(
                manager.getId(), manager.getUserName(), manager.getMobileNumber(), manager.getEmailId(),
                manager.getRole(), manager.isEnabled(),
                admin.getId(), manager.getCreatedAt(), manager.getUpdatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get shops assigned to a manager", description = "ADMIN only. Returns shops assigned " +
            "to a manager that were created by the logged-in admin.")
    @GetMapping("/{managerId}/shops")
    public ResponseEntity<List<ShopResponse>> getManagerShops(@PathVariable Long managerId,
                                                               Authentication authentication) {
        User admin = shopAccessService.getAuthenticatedUser(authentication);
        shopAccessService.ensureAdmin(admin);

        // Verify manager belongs to this admin
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new FssaiException("Manager not found", FailureCode.MANAGER_NOT_FOUND));

        if (manager.getRole() != Role.MANAGER) {
            throw new FssaiException("User is not a manager", FailureCode.INVALID_REQUEST);
        }

        List<ManagerShopAssignment> assignments =
                assignmentRepository.findByManagerIdAndAssignedByAdminId(managerId, admin.getId());

        List<ShopResponse> shops = assignments.stream()
                .filter(ManagerShopAssignment::isActive)
                .map(a -> shopService.toShopResponse(a.getShop()))
                .toList();

        return ResponseEntity.ok(shops);
    }

    @Operation(summary = "Get my assigned shops", description = "MANAGER only. Returns shops assigned to the " +
            "logged-in manager.")
    @GetMapping("/me/shops")
    public ResponseEntity<List<ShopResponse>> getMyAssignedShops(Authentication authentication) {
        User manager = shopAccessService.getAuthenticatedUser(authentication);
        shopAccessService.ensureManager(manager);

        List<ManagerShopAssignment> assignments =
                assignmentRepository.findByManagerIdAndActiveTrue(manager.getId());

        List<ShopResponse> shops = assignments.stream()
                .map(a -> shopService.toShopResponse(a.getShop()))
                .toList();

        return ResponseEntity.ok(shops);
    }

    @Operation(summary = "Deactivate manager-shop assignment", description = "ADMIN only. Deactivates " +
            "a manager's assignment to a shop without deleting the record.")
    @PutMapping("/{managerId}/shops/{shopId}/deactivate")
    public ResponseEntity<Void> deactivateAssignment(@PathVariable Long managerId,
                                                      @PathVariable Long shopId,
                                                      Authentication authentication) {
        User admin = shopAccessService.getAuthenticatedUser(authentication);
        shopAccessService.ensureAdmin(admin);

        // Verify manager belongs to this admin
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new FssaiException("Manager not found", FailureCode.MANAGER_NOT_FOUND));

        if (manager.getCreatedByAdmin() == null || !manager.getCreatedByAdmin().getId().equals(admin.getId())) {
            throw new FssaiException("You do not have permission to modify this assignment",
                    FailureCode.FORBIDDEN);
        }

        // Verify shop belongs to admin
        shopAccessService.validateShopOwner(admin, shopId);

        ManagerShopAssignment assignment = assignmentRepository.findByManagerIdAndShopId(managerId, shopId)
                .orElseThrow(() -> new FssaiException("Assignment not found", FailureCode.ASSIGNMENT_NOT_FOUND));

        assignment.setActive(false);
        assignmentRepository.save(assignment);

        return ResponseEntity.ok().build();
    }
}
