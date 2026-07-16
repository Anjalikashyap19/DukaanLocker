package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.ManagerShopAssignmentRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Reusable service for validating shop access and ownership.
 * Prevents duplication of access-control logic across controllers.
 */
@Service
public class ShopAccessService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ManagerShopAssignmentRepository assignmentRepository;

    public ShopAccessService(UserRepository userRepository,
                             ShopRepository shopRepository,
                             ManagerShopAssignmentRepository assignmentRepository) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Extracts the authenticated User from the JWT/Authentication object.
     * The emailId is used as the principal name (matching CustomUserDetailsService).
     */
    public User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new FssaiException("Authentication is required", FailureCode.UNAUTHORIZED);
        }
        String email = authentication.getName();
        return userRepository.findByEmailId(email)
                .orElseThrow(() -> new FssaiException(
                        "Authenticated user not found: " + email,
                        FailureCode.USER_NOT_FOUND));
    }

    /**
     * Returns a shop owned by the given admin, throwing 404 if not found or not owned.
     */
    public Shop getOwnedShop(Long shopId, User admin) {
        return shopRepository.findByIdAndOwnerId(shopId, admin.getId())
                .orElseThrow(() -> new FssaiException(
                        "Shop not found with id: " + shopId,
                        FailureCode.SHOP_NOT_FOUND));
    }

    /**
     * Checks whether the given user can access the given shop.
     * ADMIN users can access shops they own.
     * MANAGER users can access shops they are actively assigned to.
     */
    public boolean canAccessShop(User user, Long shopId) {
        if (user.getRole() == Role.ADMIN) {
            return shopRepository.findByIdAndOwnerId(shopId, user.getId()).isPresent();
        } else if (user.getRole() == Role.MANAGER) {
            return assignmentRepository.existsByManagerIdAndShopIdAndActiveTrue(user.getId(), shopId);
        }
        return false;
    }

    /**
     * Validates that the user can access the shop, throwing FORBIDDEN if not.
     */
    public void validateShopAccess(User user, Long shopId) {
        if (!canAccessShop(user, shopId)) {
            throw new FssaiException(
                    "You do not have access to this shop",
                    FailureCode.FORBIDDEN);
        }
    }

    /**
     * Validates that the admin actually owns the shop, throwing FORBIDDEN if not.
     */
    public void validateShopOwner(User admin, Long shopId) {
        if (admin.getRole() != Role.ADMIN) {
            throw new FssaiException(
                    "Only ADMIN users can manage shop ownership",
                    FailureCode.FORBIDDEN);
        }
        boolean isOwner = shopRepository.findByIdAndOwnerId(shopId, admin.getId()).isPresent();
        if (!isOwner) {
            throw new FssaiException(
                    "You do not own this shop",
                    FailureCode.FORBIDDEN);
        }
    }

    /**
     * Ensures the authenticated user has ADMIN role.
     */
    public void ensureAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new FssaiException(
                    "This operation requires ADMIN role",
                    FailureCode.FORBIDDEN);
        }
    }

    /**
     * Ensures the authenticated user has MANAGER role.
     */
    public void ensureManager(User user) {
        if (user.getRole() != Role.MANAGER) {
            throw new FssaiException(
                    "This operation requires MANAGER role",
                    FailureCode.FORBIDDEN);
        }
    }
}
