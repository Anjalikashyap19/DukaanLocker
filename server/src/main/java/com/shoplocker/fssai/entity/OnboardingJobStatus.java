package com.shoplocker.fssai.entity;

/**
 * Lifecycle of one FSSAI certificate auto-onboarding attempt.
 *
 * <p>{@link #SKIPPED_NO_OWNER} is the deliberate stop for certificates that do
 * not print the owner's name / mobile / email (many Form C PDFs only carry the
 * business details). Such files are never turned into accounts.</p>
 */
public enum OnboardingJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    DRY_RUN,
    SKIPPED_NO_OWNER,
    SKIPPED_DUPLICATE,
    FAILED
}
