package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {

    /**
     * Find business profile by user ID
     */
    Optional<BusinessProfile> findByUserId(Long userId);

    /**
     * Check if a user already has a business profile
     */
    boolean existsByUserId(Long userId);
}
