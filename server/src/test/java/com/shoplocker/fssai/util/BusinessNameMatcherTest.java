package com.shoplocker.fssai.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessNameMatcherTest {

    // ---- single pair (FSSAI style) ----

    @Test
    void matchesNameVariants() {
        assertTrue(BusinessNameMatcher.matches(
                "Canara Juice Junction", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertTrue(BusinessNameMatcher.matches(
                "CANARA JUICE JUNCTION", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertTrue(BusinessNameMatcher.matches(
                "Shyam Sundar", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertTrue(BusinessNameMatcher.matches(
                "Sharma Traders", "SHARMA TRADERS PRIVATE LIMITED"));
        assertTrue(BusinessNameMatcher.matches(
                "Sehgal Automobiles", "SEHGAL AUTOMOBILES"));
        assertTrue(BusinessNameMatcher.matches(
                "cafe Coffee Day", "CAFE COFFEE DAY PRIVATE LIMITED"));
    }

    @Test
    void matchesWhenEitherNameIsMissing() {
        assertTrue(BusinessNameMatcher.matches(null, "SHYAM SUNDAR"));
        assertTrue(BusinessNameMatcher.matches("My Shop", null));
        assertTrue(BusinessNameMatcher.matches("   ", "SHYAM SUNDAR"));
        assertTrue(BusinessNameMatcher.matches("My Shop", "   "));
    }

    @Test
    void rejectsDifferentNames() {
        assertFalse(BusinessNameMatcher.matches(
                "Reliance Fresh", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertFalse(BusinessNameMatcher.matches(
                "Canara Juice Corner", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertFalse(BusinessNameMatcher.matches(
                "Sharma Traders", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        // the reported bug: one shop's GSTIN used for another shop
        assertFalse(BusinessNameMatcher.matches(
                "Sehgal Automobiles", "PIQUANT CONSULTANCY SERVICES PRIVATE LIMITED"));
    }

    // ---- multi name (GST style: shop/owner vs legal/trade) ----

    @Test
    void matchesWhenAnyNameMatches() {
        // proprietorship: legal name is the person, trade name is the shop
        assertTrue(matchesShop("Sehgal Automobiles", "Kavinder Sehgal",
                "KAVINDER SEHGAL", "SEHGAL AUTOMOBILES"));
        // shop name matches the legal name
        assertTrue(matchesShop("PIQUANT CONSULTANCY SERVICES PRIVATE LIMITED", "MSME Owner",
                "PIQUANT CONSULTANCY SERVICES PRIVATE LIMITED", ""));
        // owner name matches the legal name only
        assertTrue(matchesShop("New Market", "Diya",
                "DIYA", ""));
    }

    @Test
    void rejectsWhenNoNameMatches() {
        assertFalse(matchesShop("Sehgal Automobiles", "Kavinder Sehgal",
                "PIQUANT CONSULTANCY SERVICES PRIVATE LIMITED", ""));
        assertFalse(matchesShop("Sehgal Automobiles", "Kavinder Sehgal",
                "PIQUANT CONSULTANCY SERVICES PRIVATE LIMITED", "PIQUANT CONSULTANCY"));
        assertFalse(matchesShop("Automobile", "Kavinder Sehgal",
                "PIQUANT CONSULTANCY SERVICES PRIVATE LIMITED", "PIQUANT CONSULTANCY"));
    }

    @Test
    void allowsWhenThereIsNothingToCompare() {
        // no taxpayer name at all
        assertTrue(matchesShop("Sehgal Automobiles", "Kavinder Sehgal", null, ""));
        // no shop / owner name supplied -> cannot validate, do not block
        assertTrue(matchesShop(null, null, "PIQUANT CONSULTANCY SERVICES PRIVATE LIMITED", ""));
        assertTrue(matchesShop(null, "", null, null));
    }

    private static boolean matchesShop(String shopName, String ownerName,
                                       String legalName, String tradeName) {
        return BusinessNameMatcher.matchesAny(
                Arrays.asList(shopName, ownerName),
                Arrays.asList(legalName, tradeName));
    }

    @Test
    void handlesNullCollections() {
        assertTrue(BusinessNameMatcher.matchesAny(null, Collections.singletonList("ANY")));
        assertTrue(BusinessNameMatcher.matchesAny(Collections.singletonList("ANY"), null));
        assertTrue(BusinessNameMatcher.matchesAny(Collections.emptyList(),
                Collections.singletonList("ANY")));
    }
}
