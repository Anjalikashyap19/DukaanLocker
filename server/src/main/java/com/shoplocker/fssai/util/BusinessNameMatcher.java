package com.shoplocker.fssai.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fuzzy name matching between a shop / business name the user created and a name
 * returned by a registration API (GSTN, FSSAI, Udyam, ...).
 *
 * <p>Both sides are lower-cased, stripped of punctuation and corporate suffixes
 * ("Private Limited", "Pvt Ltd", "LLP", ...) and then compared with containment in
 * either direction, so a shop named {@code "Sharma Traders"} matches a certificate
 * issued to {@code "SHARMA TRADERS PRIVATE LIMITED"}.</p>
 *
 * <p>Names containing {@code /}, {@code |}, {@code ,} or {@code &amp;} (the FSSAI
 * {@code "OWNER / BUSINESS NAME"} style) are also compared segment by segment.</p>
 */
public final class BusinessNameMatcher {

    private BusinessNameMatcher() {
    }

    /**
     * Compares a single expected name against a single API-returned name.
     * Returns {@code true} when either side is blank — there is nothing to compare,
     * so the caller is not blocked.
     */
    public static boolean matches(String expected, String actual) {
        if (isBlank(expected) || isBlank(actual)) return true;
        return strictMatch(expected, actual);
    }

    /**
     * True when at least one expected name (shop name, owner name, ...) matches at
     * least one API-returned name (legal name, trade name, ...).
     *
     * <p>Returns {@code true} when either side has no usable value: without two names
     * there is nothing to validate, so the fetch is not blocked.</p>
     */
    public static boolean matchesAny(Collection<String> expected, Collection<String> actual) {
        List<String> expectedNames = clean(expected);
        List<String> actualNames = clean(actual);
        if (expectedNames.isEmpty() || actualNames.isEmpty()) return true;

        for (String e : expectedNames) {
            for (String a : actualNames) {
                if (strictMatch(e, a)) return true;
            }
        }
        return false;
    }

    private static boolean strictMatch(String expected, String actual) {
        String a = normalize(expected);
        if (a.isEmpty()) return true; // nothing usable on our side to compare against

        String b = normalize(actual);
        if (!b.isEmpty() && contains(a, b)) return true;

        // The API may pack several names into one field: "OWNER / BUSINESS NAME"
        for (String segment : actual.split("[/|,&]")) {
            String s = normalize(segment);
            if (!s.isEmpty() && contains(a, s)) return true;
        }
        return false;
    }

    private static boolean contains(String a, String b) {
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static String normalize(String name) {
        return name.toLowerCase()
                .replaceAll("\\b(private limited|pvt limited|pvt ltd\\.?|ltd\\.?|limited|llp|inc\\.?|co\\.?|company|enterprise|enterprises|trading|traders)\\b", "")
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<String> clean(Collection<String> names) {
        List<String> cleaned = new ArrayList<>();
        if (names == null) return cleaned;
        for (String name : names) {
            if (!isBlank(name) && !normalize(name).isEmpty()) cleaned.add(name);
        }
        return cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
