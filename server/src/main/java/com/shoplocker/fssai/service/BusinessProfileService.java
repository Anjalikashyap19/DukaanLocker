package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.BusinessProfileRequest;
import com.shoplocker.fssai.dto.BusinessProfileResponse;
import com.shoplocker.fssai.entity.BusinessProfile;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.BusinessProfileRepository;
import com.shoplocker.fssai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfileService {

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Create or update a business profile for the authenticated user.
     * If a profile already exists, it will be updated; otherwise, a new one is created.
     */
    @Transactional
    public BusinessProfileResponse createOrUpdateProfile(BusinessProfileRequest request, String userEmail) {
        User user = userRepository.findByEmailId(userEmail)
                .orElseThrow(() -> new FssaiException("User not found: " + userEmail, FailureCode.USER_NOT_FOUND));

        // Validate business count
        String businessCount = request.getBusinessCount().toUpperCase();
        if (!businessCount.equals("ONE") && !businessCount.equals("MULTIPLE")) {
            throw new FssaiException("Invalid businessCount: must be ONE or MULTIPLE", FailureCode.INVALID_REQUEST);
        }

        // Validate operation scope
        String operationScope = request.getOperationScope().toUpperCase();
        if (!operationScope.equals("CITY") && !operationScope.equals("STATE") && !operationScope.equals("NATIONAL")) {
            throw new FssaiException("Invalid operationScope: must be CITY, STATE, or NATIONAL", FailureCode.INVALID_REQUEST);
        }

        // Validate business presence
        String businessPresence = request.getBusinessPresence().toUpperCase();
        if (!businessPresence.equals("SINGLE_PHYSICAL") && !businessPresence.equals("MULTIPLE_LOCATIONS") 
                && !businessPresence.equals("DIGITAL_ONLINE") && !businessPresence.equals("BOTH_PHYSICAL_DIGITAL")) {
            throw new FssaiException("Invalid businessPresence: must be SINGLE_PHYSICAL, MULTIPLE_LOCATIONS, DIGITAL_ONLINE, or BOTH_PHYSICAL_DIGITAL", FailureCode.INVALID_REQUEST);
        }

        // Check if profile already exists
        BusinessProfile profile = businessProfileRepository.findByUserId(user.getId()).orElse(null);

        if (profile == null) {
            // Create new profile
            profile = new BusinessProfile();
            profile.setUser(user);
        }

        // Update profile fields
        profile.setBusinessCount(businessCount);
        profile.setCrossCategory(request.isCrossCategory());
        profile.setMultipleBranches(request.isMultipleBranches());
        profile.setOperationScope(operationScope);
        profile.setBusinessPresence(businessPresence);

        BusinessProfile saved = businessProfileRepository.save(profile);
        return toProfileResponse(saved);
    }

    /**
     * Get business profile for the authenticated user.
     */
    public BusinessProfileResponse getProfile(String userEmail) {
        User user = userRepository.findByEmailId(userEmail)
                .orElseThrow(() -> new FssaiException("User not found: " + userEmail, FailureCode.USER_NOT_FOUND));

        BusinessProfile profile = businessProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new FssaiException("Business profile not found. Please complete the wizard.", FailureCode.WIZARD_PROFILE_NOT_FOUND));

        return toProfileResponse(profile);
    }

    /**
     * Check if a user has completed the wizard (has a business profile).
     */
    public boolean hasCompletedWizard(Long userId) {
        return businessProfileRepository.existsByUserId(userId);
    }

    /**
     * Convert entity to response DTO.
     */
    private BusinessProfileResponse toProfileResponse(BusinessProfile profile) {
        return new BusinessProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getBusinessCount(),
                profile.isCrossCategory(),
                profile.isMultipleBranches(),
                profile.getOperationScope(),
                profile.getBusinessPresence(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
