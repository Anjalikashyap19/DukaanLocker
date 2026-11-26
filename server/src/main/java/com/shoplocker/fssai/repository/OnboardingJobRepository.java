package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.OnboardingJob;
import com.shoplocker.fssai.entity.OnboardingJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OnboardingJobRepository extends JpaRepository<OnboardingJob, Long> {

    Optional<OnboardingJob> findByFileName(String fileName);

    long countByStatus(OnboardingJobStatus status);

    List<OnboardingJob> findByStatusAndCreatedAtBetween(OnboardingJobStatus status,
                                                        LocalDateTime from,
                                                        LocalDateTime to);

    List<OnboardingJob> findByStatus(OnboardingJobStatus status);
}
