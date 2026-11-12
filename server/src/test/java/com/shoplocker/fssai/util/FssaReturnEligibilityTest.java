package com.shoplocker.fssai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FssaReturnEligibilityTest {

    @Test
    void manufacturerAndImporterKobsAreLiable() {
        assertTrue(FssaReturnEligibility.isApplicable("Manufacturer", null));
        assertTrue(FssaReturnEligibility.isApplicable("Manufacturer - Repacker", null));
        assertTrue(FssaReturnEligibility.isApplicable("Manufacturer - Relabeller", null));
        assertTrue(FssaReturnEligibility.isApplicable("Importer", null));
        assertTrue(FssaReturnEligibility.isApplicable("Imports - Importer", null));
        assertTrue(FssaReturnEligibility.isApplicable("Exporter - Manufacturer", null));
        assertTrue(FssaReturnEligibility.isApplicable("100% Export Oriented Units", null));
    }

    @Test
    void merchantExporterAndTradeKobsAreNotLiable() {
        assertFalse(FssaReturnEligibility.isApplicable("Merchant Exporter", null));
        assertFalse(FssaReturnEligibility.isApplicable("Trade/Retail - Retailer", null));
        assertFalse(FssaReturnEligibility.isApplicable("Trade/Retail - Wholesaler", null));
        assertFalse(FssaReturnEligibility.isApplicable("Food Service - Caterer", null));
    }

    @Test
    void eligibleKobWinsInAMultiKobLicence() {
        assertTrue(FssaReturnEligibility.isApplicable(
                "Trade/Retail - Retailer, Manufacturer - Repacker", null));
    }

    @Test
    void unknownKobFallsBackToCategory() {
        assertTrue(FssaReturnEligibility.isApplicable(null, "FOOD MANUFACTURING & PROCESSING"));
        assertTrue(FssaReturnEligibility.isApplicable("", "IMPORT_EXPORT"));
        assertFalse(FssaReturnEligibility.isApplicable(null, "FOOD RETAIL & GROCERY"));
        assertFalse(FssaReturnEligibility.isApplicable(null, "CONTRACTORS, BUILDERS & DEVELOPERS"));
    }

    @Test
    void recognisedRetailerKobOverridesManufacturingCategory() {
        assertFalse(FssaReturnEligibility.isApplicable(
                "Trade/Retail - Retailer", "FOOD MANUFACTURING & PROCESSING"));
    }
}
