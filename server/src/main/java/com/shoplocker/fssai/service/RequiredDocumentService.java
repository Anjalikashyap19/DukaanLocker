package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.BusinessScale;
import com.shoplocker.fssai.entity.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RequiredDocumentService.class);

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

        // GROCERY — Food retail, wholesale, fruits & vegetables need FSSAI license
        Set<DocumentType> grocery = new HashSet<>();
        Collections.addAll(grocery,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.FSSAI_FOOD_LICENSE,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("GROCERY", grocery);

        // RESTAURANT — Hotels & eateries need FSSAI + fire safety
        Set<DocumentType> restaurant = new HashSet<>();
        Collections.addAll(restaurant,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.FSSAI_FOOD_LICENSE,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.FIRE_SAFETY,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("RESTAURANT", restaurant);

        // IMPORT_EXPORT — Logistics & agriculture need IEC instead of shop insurance
        Set<DocumentType> importExport = new HashSet<>();
        Collections.addAll(importExport,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.IEC,
                DocumentType.MSME,
                DocumentType.TRADE_LICENSE);
        CATEGORY_DOCUMENTS.put("IMPORT_EXPORT", importExport);

        // MANUFACTURING — Factories & workshops need pollution, fire, IEC
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

        // MEDICAL / PHARMACY — Healthcare & pharmacy need drug license
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

        // CLOTHING — Garments & textiles, base set
        Set<DocumentType> clothing = new HashSet<>();
        Collections.addAll(clothing,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("CLOTHING", clothing);

        // FASHION — Jewellery & cosmetics need trademark for brand protection
        Set<DocumentType> fashion = new HashSet<>();
        Collections.addAll(fashion,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.TRADEMARK,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("FASHION", fashion);

        // ELECTRONICS — Electrical & telecom need trademark for brand compliance
        Set<DocumentType> electronics = new HashSet<>();
        Collections.addAll(electronics,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.TRADEMARK,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("ELECTRONICS", electronics);

        // HARDWARE — Construction & industrial goods need pollution control
        Set<DocumentType> hardware = new HashSet<>();
        Collections.addAll(hardware,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.POLLUTION_CONTROL,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("HARDWARE", hardware);

        // BEAUTY — Salons & personal care need professional tax
        Set<DocumentType> beauty = new HashSet<>();
        Collections.addAll(beauty,
                DocumentType.PAN,
                DocumentType.GST,
                DocumentType.TRADE_LICENSE,
                DocumentType.MSME,
                DocumentType.PROFESSIONAL_TAX,
                DocumentType.SHOP_INSURANCE);
        CATEGORY_DOCUMENTS.put("BEAUTY", beauty);

        // GENERAL STORE (default) — Retail, IT, education, banking etc.
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
            log.info("Category alias resolved: '{}' -> '{}' ({} docs)", key, alias, CATEGORY_DOCUMENTS.getOrDefault(alias, DEFAULT_DOCUMENTS).size());
            key = alias;
        } else {
            log.warn("No alias found for category '{}' — falling back to raw key lookup", key);
        }
        Set<DocumentType> docs = CATEGORY_DOCUMENTS.get(key);
        if (docs != null) {
            log.info("Found document set for key '{}': {} docs", key, docs.size());
            return new HashSet<>(docs);
        }

        // For unknown categories, return the default set
        log.warn("Unknown category key '{}' — returning default {} docs", key, DEFAULT_DOCUMENTS.size());
        return new HashSet<>(DEFAULT_DOCUMENTS);
    }
}
