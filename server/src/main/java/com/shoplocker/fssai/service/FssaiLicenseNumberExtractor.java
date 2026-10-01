package com.shoplocker.fssai.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovers the 14-digit FSSAI licence number from OCR text.
 *
 * <p>Needed on the self-upload path: when the user uploads their own certificate
 * the app cannot ask them to retype the number, and a state/registration
 * licence has no expiry date printed on it, so the only way to recover the
 * expiry is to look the number up upstream.</p>
 *
 * <p>The 14-digit run is located near the licence-number label first, because
 * OCR text of a certificate is full of other long digit strings (dates, the FBO
 * id, reference ids, phone numbers) and guessing wrong would query the upstream
 * for a number that does not exist. Falls back to the first standalone 14-digit
 * token when the label is not legible.</p>
 */
@Component
public class FssaiLicenseNumberExtractor {

    /** A licence number is exactly 14 digits, not part of a longer run. */
    private static final Pattern NUMBER_14 = Pattern.compile("(?<!\\d)(\\d{14})(?!\\d)");

    /**
     * Label forms seen across real certificates and their OCR output. Tried in
     * order; the digits that follow the nearest label win.
     */
    private static final String[] LICENSE_LABELS = {
            "FSSAI LICENSE / REGISTRATION NUMBER",
            "FSSAI LICENSE/REGISTRATION NUMBER",
            "FSSAI LICENSE NUMBER",
            "FSSAI LICENCE NUMBER",
            "FSSAI REGISTRATION NUMBER",
            "LICENSE / REGISTRATION NUMBER",
            "LICENCE / REGISTRATION NUMBER",
            "LICENSE NUMBER",
            "LICENCE NUMBER",
            "REGISTRATION NUMBER",
            "LICENSE NO",
            "LICENCE NO",
            "FSSAI NO",
    };

    /**
     * @param ocrText text extracted from the uploaded document
     * @return the licence number, or empty when none is present
     */
    public Optional<String> extract(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return Optional.empty();
        }

        // 1. Prefer a number that sits next to a licence-number label.
        String upper = ocrText.toUpperCase();
        for (String label : LICENSE_LABELS) {
            int from = 0;
            while (true) {
                int at = upper.indexOf(label, from);
                if (at < 0) break;
                // Look a short window after the label; OCR often runs the label
                // straight into the digits ("...NUMBER10121029000284").
                Matcher m = NUMBER_14.matcher(ocrText);
                int limit = Math.min(ocrText.length(), at + label.length() + 40);
                while (m.find(at + label.length()) && m.start() < limit) {
                    return Optional.of(m.group(1));
                }
                from = at + label.length();
            }
        }

        // 2. Otherwise accept the first standalone 14-digit token.
        Matcher m = NUMBER_14.matcher(ocrText);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
