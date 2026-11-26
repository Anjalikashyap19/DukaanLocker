package com.shoplocker.fssai.event;

/**
 * Published inside the account-creation transaction and consumed after commit,
 * so an owner never receives credentials for an account that rolled back.
 *
 * @param jobId         the onboarding job row to annotate with the mail outcome
 * @param userId        the created owner
 * @param emailId       recipient; the email printed on the certificate
 * @param rawPassword   the plaintext password (never persisted anywhere)
 * @param ownerName     owner name from the certificate
 * @param shopName      shop name from the certificate
 * @param licenseNumber FSSAI licence number from the certificate
 * @param placeholderContact true when email/mobile were generated because the
 *                            certificate did not print the owner's real details
 */
public record AccountCredentialsEvent(Long jobId,
                                      Long userId,
                                      String emailId,
                                      String rawPassword,
                                      String ownerName,
                                      String shopName,
                                      String licenseNumber,
                                      boolean placeholderContact) {
}
