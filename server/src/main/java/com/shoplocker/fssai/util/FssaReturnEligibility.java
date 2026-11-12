package com.shoplocker.fssai.util;

import java.util.Locale;

/**
 * Decides whether a shop must file the FSSAI annual return (Form D-1).
 *
 * <p>FSSAI's advisory of 16 April 2024 limits Form D-1 to
 * "Manufacturers [including Repacker and Relabeller], Importer and
 * Manufacturer-Exporter". The licence's Kind of Business (KOB) is the
 * authoritative signal and is returned by the verification API as
 * {@code kobname}, formatted {@code "Major group - Sub KOB"} (for example
 * {@code "Trade/Retail - Retailer"}). A licence can carry several KOBs, so the
 * value is treated as a comma-separated list.</p>
 *
 * <p>{@code Merchant Exporter} is deliberately excluded: it files a quarterly
 * report, not the annual Form D-1. When the KOB is blank or unrecognised the
 * shop category is used as a fallback, per product decision.</p>
 */
public final class FssaReturnEligibility {

    private FssaReturnEligibility() {}

    public static boolean isApplicable(String kindOfBusiness, String shopCategory) {
        Boolean fromKob = fromKindOfBusiness(kindOfBusiness);
        if (fromKob != null) {
            return fromKob;
        }
        return fromCategory(shopCategory);
    }

    /**
     * @return {@code TRUE}/{@code FALSE} when the KOB is present, or
     *         {@code null} when it is blank/unrecognised so the caller can fall
     *         back to the shop category.
     */
    static Boolean fromKindOfBusiness(String kindOfBusiness) {
        if (kindOfBusiness == null || kindOfBusiness.isBlank()) {
            return null;
        }

        boolean recognizedIneligible = false;
        for (String raw : kindOfBusiness.split(",")) {
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) {
                continue;
            }

            // Merchant Exporter files quarterly, not Form D-1.
            if (entry.contains("merchant export")) {
                recognizedIneligible = true;
                continue;
            }

            int dash = entry.indexOf(" - ");
            String group = dash > 0 ? entry.substring(0, dash).trim() : entry;

            if (group.equals("manufacturer") || group.equals("imports") || group.equals("importer")) {
                return true;
            }
            if (entry.contains("manufactur")
                    || entry.contains("repack")
                    || entry.contains("relabel")
                    || entry.contains("export oriented")
                    || entry.contains("dairy unit")
                    || entry.equals("importer")
                    || entry.equals("import")) {
                return true;
            }

            if (group.equals("trade/retail")
                    || group.equals("trade")
                    || group.equals("retail")
                    || group.equals("food service")
                    || group.equals("storage")
                    || group.equals("transport")
                    || group.equals("transporter")
                    || group.equals("head office")
                    || group.equals("head office/registered ecommerce")
                    || group.equals("distributor")
                    || group.equals("wholesaler")
                    || group.equals("supplier")
                    || group.equals("marketer")) {
                recognizedIneligible = true;
            }
        }

        // Recognised non-eligible KOB wins over the category fallback. A KOB we
        // cannot classify at all returns null so the category decides.
        return recognizedIneligible ? Boolean.FALSE : null;
    }

    /** Conservative fallback: only categories that clearly imply a Form D-1 activity. */
    static boolean fromCategory(String shopCategory) {
        if (shopCategory == null || shopCategory.isBlank()) {
            return false;
        }
        String category = shopCategory.toLowerCase(Locale.ROOT);

        // Non-food "manufacturing" categories (contractors, builders, industrial
        // workshops) are not FSSAI licensees.
        if (category.contains("contractor")
                || category.contains("builder")
                || category.contains("developer")
                || category.contains("workshop")
                || category.contains("industrial")) {
            return false;
        }

        return category.contains("food manufactur")
                || category.contains("food processing")
                || category.contains("import")
                || category.contains("export")
                || category.contains("repack")
                || category.contains("relabel");
    }
}
