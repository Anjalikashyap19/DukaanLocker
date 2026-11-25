package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.OnboardingJob;
import com.shoplocker.fssai.entity.OnboardingJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OnboardingJobRepository extends JpaRepository<OnboardingJob, Long> {

    Optional<OnboardingJob> findByFileName(String fileName);

    long countByStatus(OnboardingJobStatus status);
}
