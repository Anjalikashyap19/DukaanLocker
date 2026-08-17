package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findTopByMobileAndPurposeOrderByCreatedAtDesc(String mobile, String purpose);

    void deleteByMobileAndPurpose(String mobile, String purpose);
}
