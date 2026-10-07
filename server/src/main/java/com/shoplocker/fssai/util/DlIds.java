package com.shoplocker.fssai.util;

/**
 * DL ID (Dukaan Locker ID) — the 4-digit folder identifier for a user.
 *
 * <p>Formula: {@code userId + 1110}, zero-padded to 4 digits, so the first
 * user gets 1111 and the 0001-1110 range stays reserved (see
 * {@code LocalFileStorageService}). This is the single place that formula
 * lives: storage paths, the users.dl_id column and renewal orders must all
 * agree on it.
 */
public final class DlIds {

    private static final int OFFSET = 1110;

    private DlIds() {}

    /** Returns the DL ID for the given user id (e.g. 1 -&gt; "1111"). */
    public static String forUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        return String.format("%04d", userId + OFFSET);
    }

    /** True when a stored DL ID is missing/blank and still needs to be assigned. */
    public static boolean isMissing(String dlId) {
        return dlId == null || dlId.isBlank();
    }
}
