package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.BusinessScale;
import com.shoplocker.fssai.entity.DocumentType;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Determines which documents are required for a shop based on its category and business scale.
 * Uses a configurable map that can be expanded over time.
 */
@Service
public class RequiredDocumentService {

    private static final Map<String, Set<DocumentType>> CATEGORY_DOCUMENTS = new HashMap<>();
    private static final Set<DocumentType> DEFAULT_DOCUMENTS = new HashSet<>();

    // Maps frontend display names to internal category keys
    private static final Map<String, String> CATEGORY_ALIASES = new HashMap<>();

    static {
        CATEGORY_ALIASES.put("FOOD RETAIL & GROCERY", "GROCERY");
        CATEGORY_ALIASES.put("FOOD WHOLESALE, DISTRIBUTION & SUPPLY", "GROCERY");
        CATEGORY_ALIASES.put("RESTAURANTS, HOTELS & CATERING", "RESTAURANT");
        CATEGORY_ALIASES.put("BAKERY, SWEETS & CONFECTIONERY", "RESTAURANT");
        CATEGORY_ALIASES.put("BEVERAGES, DAIRY & PACKAGED WATER", "RESTAURANT");
        CATEGORY_ALIASES.put("MEAT, FISH, POULTRY & LIVESTOCK", "RESTAURANT");
        CATEGORY_ALIASES.put("FRUITS, VEGETABLES & AGRICULTURAL PRODUCE", "GROCERY");
        CATEGORY_ALIASES.put("FOOD MANUFACTURING & PROCESSING", "MANUFACTURING");
        CATEGORY_ALIASES.put("MANUFACTURING, WORKSHOPS & INDUSTRIAL ACTIVITIES", "MANUFACTURING");
        CATEGORY_ALIASES.put("HEALTHCARE, CLINICS & DIAGNOSTICS", "MEDICAL");
        CATEGORY_ALIASES.put("PHARMACY, MEDICINES & MEDICAL EQUIPMENT", "PHARMACY");
        CATEGORY_ALIASES.put("ELECTRONICS, ELECTRICAL & TELECOM", "ELECTRONICS");
        CATEGORY_ALIASES.put("GARMENTS, TEXTILE & TAILORING", "CLOTHING");
        CATEGORY_ALIASES.put("JEWELLERY, COSMETICS & FASHION ACCESSORIES", "FASHION");
        CATEGORY_ALIASES.put("CONSTRUCTION MATERIALS, HARDWARE & INDUSTRIAL GOODS", "HARDWARE");
        CATEGORY_ALIASES.put("BEAUTY, SALON & PERSONAL CARE", "BEAUTY");
        CATEGORY_ALIASES.put("GENERAL RETAIL & VARIETY STORES", "GENERAL_STORE");
        CATEGORY_ALIASES.put("STATIONERY, BOOKS, PRINTING & PUBLISHING", "GENERAL_STORE");
        CATEGORY_ALIASES.put("COURIER, LOGISTICS & WAREHOUSING", "IMPORT_EXPORT");
        CATEGORY_ALIASES.put("AGRICULTURE INPUTS & ALLIED ACTIVITIES", "IMPORT_EXPORT");
        CATEGORY_ALIASES.put("CORPORATE OFFICES & COMMERCIAL ESTABLISHMENTS", "GENERAL_STORE");
        CATEGORY_ALIASES.put("BANKING, FINANCE & INSURANCE", "GENERAL_STORE");
        CATEGORY_ALIASES.put("PROFESSIONAL & CONSULTANCY SERVICES", "GENERAL_STORE");
        CATEGORY_ALIASES.put("CONTRACTORS, BUILDERS & DEVELOPERS", "MANUFACTURING");
        CATEGORY_ALIASES.put("LABOUR, SECURITY & MANPOWER SERVICES", "GENERAL_STORE");
        CATEGORY_ALIASES.put("EDUCATION & TRAINING", "GENERAL_STORE");
        CATEGORY_ALIASES.put("NGO, WELFARE & RESEARCH ORGANISATIONS", "GENERAL_STORE");
        CATEGORY_ALIASES.put("IT, SOFTWARE & DIGITAL SERVICES", "GENERAL_STORE");
        CATEGORY_ALIASES.put("REPAIR, MAINTENANCE & TECHNICAL SERVICES", "GENERAL_STORE");
        CATEGORY_ALIASES.put("HOTELS, LODGING & HOSPITALITY", "RESTAURANT");
        CATEGORY_ALIASES.put("AUTOMOBILE, TRANSPORT & TRAVEL", "GENERAL_STORE");
        CATEGORY_ALIASES.put("MARRIAGE, BANQUET & EVENT SERVICES", "GENERAL_STORE");
    }

    static {
        // Default set used when a category has no specific map entry
        Collections.addAll(DEFAULT_DOCUMENTS,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);

        // GROCERY
        Set<DocumentType> grocery = new HashSet<>();
        Collections.addAll(grocery,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.FSSAI_FOOD_LICENSE,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("GROCERY", grocery);

        // RESTAURANT
        Set<DocumentType> restaurant = new HashSet<>();
        Collections.addAll(restaurant,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.FSSAI_FOOD_LICENSE,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("RESTAURANT", restaurant);

        // IMPORT_EXPORT
        Set<DocumentType> importExport = new HashSet<>();
        Collections.addAll(importExport,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.IEC,
                DocumentType.MSME,
                DocumentType.TRADE_LICENSE);
        CATEGORY_DOCUMENTS.put("IMPORT_EXPORT", importExport);

        // MANUFACTURING
        Set<DocumentType> manufacturing = new HashSet<>();
        Collections.addAll(manufacturing,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.IEC,
                DocumentType.POLLUTION_CONTROL,
                DocumentType.FIRE_SAFETY,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("MANUFACTURING", manufacturing);

        // MEDICAL / PHARMACY
        Set<DocumentType> medical = new HashSet<>();
        Collections.addAll(medical,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.DRUG_LICENSE,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("MEDICAL", medical);
        CATEGORY_DOCUMENTS.put("PHARMACY", medical);

        // CLOTHING / FASHION
        Set<DocumentType> clothing = new HashSet<>();
        Collections.addAll(clothing,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("CLOTHING", clothing);
        CATEGORY_DOCUMENTS.put("FASHION", clothing);

        // ELECTRONICS
        Set<DocumentType> electronics = new HashSet<>();
        Collections.addAll(electronics,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("ELECTRONICS", electronics);

        // HARDWARE
        Set<DocumentType> hardware = new HashSet<>();
        Collections.addAll(hardware,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("HARDWARE", hardware);

        // SALON / BEAUTY
        Set<DocumentType> salon = new HashSet<>();
        Collections.addAll(salon,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("SALON", salon);
        CATEGORY_DOCUMENTS.put("BEAUTY", salon);

        // GENERAL STORE (default)
        Set<DocumentType> general = new HashSet<>();
        Collections.addAll(general,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("GENERAL_STORE", general);
    }

    /**
     * Returns the set of required DocumentTypes for a given shop category and scale.
     * Category is matched case-insensitively. Unknown categories get the default document set.
     */
    public Set<DocumentType> getRequiredDocuments(String category, BusinessScale scale) {
        if (category == null) {
            return new HashSet<>(DEFAULT_DOCUMENTS);
        }

        String key = category.trim().toUpperCase();
        // Resolve frontend display name to internal key if an alias exists
        String alias = CATEGORY_ALIASES.get(key);
        if (alias != null) {
            key = alias;
        }
        Set<DocumentType> docs = CATEGORY_DOCUMENTS.get(key);
        if (docs != null) {
            return new HashSet<>(docs);
        }

        // For unknown categories, return the default set
        return new HashSet<>(DEFAULT_DOCUMENTS);
    }
}
