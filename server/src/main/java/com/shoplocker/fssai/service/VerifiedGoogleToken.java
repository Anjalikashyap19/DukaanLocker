package com.shoplocker.fssai.service;

/**
 * Claims extracted from a Google ID token after server-side verification.
 */
public record VerifiedGoogleToken(String subject, String email, boolean emailVerified) {
}
