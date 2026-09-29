package com.shoplocker.fssai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort expiry-date extraction from OCR text of uploaded documents.
 *
 * Only dates found in a window right after an explicit validity keyword
 * ("valid till", "expiry date", ...) are trusted, so issue dates, file dates
 * and numbers are never mistaken for an expiry. Indian documents commonly use
 * dd/MM/yyyy, dd-MMM-yyyy and "30 October 2026" styles - all handled.
 */
@Component
public class ExpiryDateExtractor {

    private static final Logger log = LoggerFactory.getLogger(ExpiryDateExtractor.class);

    /** Validity keywords (lowercase); a date is searched within the following ~60 chars. */
    private static final List<String> KEYWORDS = List.of(
            "valid up to", "valid upto", "valid till", "valid until", "validity till",
            "date of expiry", "expiry date", "expires on", "expire on", "expiry",
            "valid from", "period of validity", "validity");

    private static final List<String> PERMANENT = List.of(
            "lifetime", "life time", "permanent", "valid for life");

    private static final Pattern DAY_FIRST =
            Pattern.compile("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})\\b");
    private static final Pattern YEAR_FIRST =
            Pattern.compile("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b");
    private static final Pattern DAY_MONTH_YEAR =
            Pattern.compile("\\b(\\d{1,2})[-\\s]([A-Za-z]{3,9})[a-z]*[.,-]?[-\\s]+(\\d{4})\\b");
    private static final Pattern MONTH_DAY_YEAR =
            Pattern.compile("\\b([A-Za-z]{3,9})[a-z]*\\s+(\\d{1,2}),?\\s+(\\d{4})\\b");
    private static final Pattern MONTH_YEAR =
            Pattern.compile("\\b([A-Za-z]{3,9})[a-z]*[\\s-]+(\\d{4})\\b");

    /**
     * @param ocrText text extracted from the uploaded document
     * @return the expiry date at midnight if confidently found
     */
    public Optional<LocalDateTime> extract(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) return Optional.empty();
        String lower = ocrText.toLowerCase(Locale.ENGLISH);

        for (String keyword : KEYWORDS) {
            int from = 0;
            int idx;
            while ((idx = lower.indexOf(keyword, from)) >= 0) {
                from = idx + keyword.length();
                int end = Math.min(ocrText.length(), from + 60);
                String window = ocrText.substring(idx, end);

                if (isPermanent(window)) return Optional.empty();   // "valid till lifetime"

                Optional<LocalDate> parsed = parseWindow(window);
                if (parsed.isPresent()) {
                    log.debug("Extracted expiry {} from OCR near keyword '{}'",
                            parsed.get(), keyword);
                    return Optional.of(parsed.get().atStartOfDay());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isPermanent(String window) {
        String lower = window.toLowerCase(Locale.ENGLISH);
        return PERMANENT.stream().anyMatch(lower::contains);
    }

    private Optional<LocalDate> parseWindow(String window) {
        // "valid from A to B" - the expiry is the date after the last " to "
        String scope = window;
        int toIdx = lastIndexIgnoreCase(scope, " to ");
        if (toIdx >= 0) {
            scope = scope.substring(toIdx + 4);
        }

        Optional<LocalDate> result = tryPattern(scope, YEAR_FIRST, true);
        if (result.isPresent()) return result;

        result = tryPattern(scope, DAY_FIRST, false);
        if (result.isPresent()) return result;

        result = tryDayMonthNameYear(scope);
        if (result.isPresent()) return result;

        result = tryMonthDayYear(scope);
        if (result.isPresent()) return result;

        // Month + year only (e.g. "Oct 2026") -> last day of that month
        Matcher my = MONTH_YEAR.matcher(scope);
        if (my.find()) {
            try {
                YearMonth ym = YearMonth.of(
                        Integer.parseInt(my.group(2)),
                        monthFromName(my.group(1)));
                return Optional.of(ym.atEndOfMonth());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Optional.empty();
    }

    private Optional<LocalDate> tryDayMonthNameYear(String scope) {
        // "30 October 2026" / "30-Oct-2026"
        Matcher m = DAY_MONTH_YEAR.matcher(scope);
        if (!m.find()) return Optional.empty();
        try {
            return validated(LocalDate.of(
                    Integer.parseInt(m.group(3)),
                    monthFromName(m.group(2)),
                    Integer.parseInt(m.group(1))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<LocalDate> tryMonthDayYear(String scope) {
        // "October 30, 2026" / "Oct 30 2026"
        Matcher m = MONTH_DAY_YEAR.matcher(scope);
        if (!m.find()) return Optional.empty();
        try {
            return validated(LocalDate.of(
                    Integer.parseInt(m.group(3)),
                    monthFromName(m.group(1)),
                    Integer.parseInt(m.group(2))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<LocalDate> validated(LocalDate date) {
        // Reject clearly implausible dates (OCR noise) - docs rarely expire
        // before 2000 or more than 30 years out
        if (date.getYear() < 2000 || date.getYear() > LocalDate.now().getYear() + 30) {
            return Optional.empty();
        }
        return Optional.of(date);
    }

    private Optional<LocalDate> tryPattern(String scope, Pattern pattern, boolean yearFirst) {
        Matcher m = pattern.matcher(scope);
        if (!m.find()) return Optional.empty();
        try {
            LocalDate date;
            if (yearFirst) {
                date = LocalDate.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            } else {
                int a = Integer.parseInt(m.group(1));
                int b = Integer.parseInt(m.group(2));
                // Indian docs are day-first; only flip when the value demands it
                if (a > 12 && b <= 12) {
                    date = LocalDate.of(Integer.parseInt(m.group(3)), b, a);
                } else if (b > 12 && a <= 12) {
                    date = LocalDate.of(Integer.parseInt(m.group(3)), a, b);
                } else {
                    date = LocalDate.of(Integer.parseInt(m.group(3)), b, a);
                }
            }
            return validated(date);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private int monthFromName(String name) {
        String n = name.substring(0, 3).toLowerCase(Locale.ENGLISH);
        return switch (n) {
            case "jan" -> 1; case "feb" -> 2; case "mar" -> 3; case "apr" -> 4;
            case "may" -> 5; case "jun" -> 6; case "jul" -> 7; case "aug" -> 8;
            case "sep" -> 9; case "oct" -> 10; case "nov" -> 11; case "dec" -> 12;
            default -> throw new IllegalArgumentException("Unknown month: " + name);
        };
    }

    private int lastIndexIgnoreCase(String text, String needle) {
        String lower = text.toLowerCase(Locale.ENGLISH);
        return lower.lastIndexOf(needle.toLowerCase(Locale.ENGLISH));
    }
}
