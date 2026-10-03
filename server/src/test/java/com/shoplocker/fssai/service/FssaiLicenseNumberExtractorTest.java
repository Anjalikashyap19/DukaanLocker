package com.shoplocker.fssai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers recovering the 14-digit licence number from OCR text, which is what
 * makes the expiry fallback work on the self-upload path (a state/registration
 * licence prints no expiry date, so the number is the only way to look one up).
 */
class FssaiLicenseNumberExtractorTest {

    private final FssaiLicenseNumberExtractor extractor = new FssaiLicenseNumberExtractor();

    /** Shape taken from a real state licence whose OCR yielded no expiry date. */
    private static final String OCR_STATE_LICENSE = """
            FSSAI LICENSE / REGISTRATION NUMBER10121029000284
            NAME OF FOOD BUSINESS OPERATOR
            SRI KRISHNA SWEETS
            ADDRESS: MAIN ROAD, UDUPI
            DATE OF REGISTRATION 07-09-2011
            CATEGORY OF FOOD BUSINESS: RETAILER
            """;

    @Test
    void pullsNumberPrintedStraightAfterTheLabel() {
        assertEquals("10121029000284", extractor.extract(OCR_STATE_LICENSE).orElseThrow());
    }

    @Test
    void toleratesWhitespaceAndNewlinesBetweenLabelAndNumber() {
        String text = """
                FSSAI LICENSE NUMBER :
                    21221160000115
                """;

        assertEquals("21221160000115", extractor.extract(text).orElseThrow());
    }

    @Test
    void handlesTheLicenceSpelling() {
        assertEquals("21221160000115",
                extractor.extract("FSSAI LICENCE NO 21221160000115").orElseThrow());
    }

    /**
     * The certificate is full of other long digit runs. A number attached to the
     * licence label must win over one that merely appears first in the text.
     */
    @Test
    void prefersNumberNearLabelOverOtherDigitRuns() {
        String text = """
                FBO ID 7289228712345
                DATE OF REGISTRATION 07-09-2011
                FSSAI LICENSE / REGISTRATION NUMBER 21221160000115
                """;

        assertEquals("21221160000115", extractor.extract(text).orElseThrow());
    }

    @Test
    void fallsBackToFirstStandaloneFourteenDigitToken() {
        assertEquals("21221160000115",
                extractor.extract("Certificate copy 21221160000115 issued").orElseThrow());
    }

    @Test
    void ignoresDigitRunsThatAreNotExactlyFourteenDigits() {
        // 13 and 15 digit runs are not licence numbers
        assertTrue(extractor.extract("Phone 9876543210123 PAN ABCDE1234F").isEmpty());
        assertTrue(extractor.extract("Reference 123456789012345").isEmpty());
        // A 14-digit run embedded in a longer number is not a standalone token
        assertTrue(extractor.extract("Code 9912345678901234").isEmpty());
    }

    @Test
    void returnsEmptyForTextWithoutAnyNumber() {
        assertTrue(extractor.extract(null).isEmpty());
        assertTrue(extractor.extract("").isEmpty());
        assertTrue(extractor.extract("   ").isEmpty());
        assertTrue(extractor.extract("FSSAI Food License, Food Business Operator").isEmpty());
    }
}
