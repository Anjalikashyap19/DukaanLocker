package com.iadv.dukaanlocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ─── Data Models ─────────────────────────────────────────────────────────────

data class UserAccount(
    val mobile: String,
    val name: String,
    val email: String = "",
    val password: String = "",
    val role: String,           // "OWNER" or "MANAGER"
    val managerCode: String = ""
)

data class WizardAnswers(
    val businessCount: String = "ONE",          // "ONE" or "MULTIPLE"
    val crossCategory: Boolean = false,
    val multipleBranches: Boolean = false,
    val operationScope: String = "CITY",        // "CITY", "STATE", "NATIONAL"
    val digitalReadiness: String = "PHYSICAL",  // "PHYSICAL", "SCATTERED", "DIGITAL"
    val totalBusinesses: Int = 1
)

data class BusinessProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val ownerName: String = "",
    val category: String = "Grocery/Kirana",
    val scale: String = "Micro",
    val state: String = "Maharashtra",
    val city: String = "",
    val branchName: String = ""
)

data class ManagerAccess(
    val code: String,
    val managerName: String,
    val assignedBusinessIds: List<String>,
    val id: String? = null
)

data class ShopDetails(
    val name: String,
    val owner: String,
    val category: String,
    val scale: String,
    val state: String
)

data class DocumentItem(
    val id: String = UUID.randomUUID().toString(),
    val businessId: String = "",
    val type: String = "",
    val name: String = "",
    val status: String = "MISSING",  // "MISSING", "FETCHED", "UPLOADED"
    val regNumber: String = "",
    val expiryDate: String = "",
    val issueDate: String = "",
    val fileUrl: String? = null
)

// ─── Storage ──────────────────────────────────────────────────────────────────

object LockerStorage {
    private const val PREFS = "dukaan_locker_v2"
    private const val K_USER = "user"
    private const val K_WIZARD = "wizard"
    private const val K_WIZARD_DONE = "wizard_done"
    private const val K_BUSINESSES = "businesses"
    private const val K_MANAGERS = "managers"
    private const val K_DOCS = "documents"
    private const val K_THEME = "dark_theme"

    // ── User ──────────────────────────────────────────────────────────────────
    fun saveUser(ctx: Context, u: UserAccount) {
        pref(ctx).edit().putString(K_USER, JSONObject().apply {
            put("mobile", u.mobile); put("name", u.name)
            put("email", u.email); put("password", u.password)
            put("role", u.role); put("managerCode", u.managerCode)
        }.toString()).apply()
    }

    fun getUser(ctx: Context): UserAccount? = try {
        pref(ctx).getString(K_USER, null)?.let { s ->
            JSONObject(s).let {
                UserAccount(
                    it.getString("mobile"), it.getString("name"),
                    it.optString("email", ""), it.optString("password", ""),
                    it.getString("role"), it.optString("managerCode", "")
                )
            }
        }
    } catch (e: Exception) { null }

    fun clearUser(ctx: Context) = pref(ctx).edit().remove(K_USER).apply()

    // ── Wizard ────────────────────────────────────────────────────────────────
    fun saveWizard(ctx: Context, w: WizardAnswers) {
        pref(ctx).edit()
            .putString(K_WIZARD, JSONObject().apply {
                put("businessCount", w.businessCount)
                put("crossCategory", w.crossCategory)
                put("multipleBranches", w.multipleBranches)
                put("operationScope", w.operationScope)
                put("digitalReadiness", w.digitalReadiness)
                put("totalBusinesses", w.totalBusinesses)
            }.toString())
            .putBoolean(K_WIZARD_DONE, true)
            .apply()
    }

    fun getWizard(ctx: Context): WizardAnswers? = try {
        pref(ctx).getString(K_WIZARD, null)?.let { s ->
            JSONObject(s).let {
                WizardAnswers(
                    it.getString("businessCount"), it.getBoolean("crossCategory"),
                    it.getBoolean("multipleBranches"), it.getString("operationScope"),
                    it.getString("digitalReadiness"), it.optInt("totalBusinesses", 1)
                )
            }
        }
    } catch (e: Exception) { null }

    fun isWizardDone(ctx: Context): Boolean = pref(ctx).getBoolean(K_WIZARD_DONE, false)

    // ── Businesses ────────────────────────────────────────────────────────────
    fun saveBusinesses(ctx: Context, list: List<BusinessProfile>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id); put("name", b.name); put("ownerName", b.ownerName)
                put("category", b.category); put("scale", b.scale)
                put("state", b.state); put("city", b.city); put("branchName", b.branchName)
            })
        }
        pref(ctx).edit().putString(K_BUSINESSES, arr.toString()).apply()
    }

    fun getBusinesses(ctx: Context): List<BusinessProfile> = try {
        val str = pref(ctx).getString(K_BUSINESSES, null) ?: return emptyList()
        val arr = JSONArray(str)
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let {
                BusinessProfile(it.getString("id"), it.getString("name"),
                    it.getString("ownerName"), it.getString("category"), it.getString("scale"),
                    it.getString("state"), it.getString("city"), it.optString("branchName", ""))
            }
        }
    } catch (e: Exception) { emptyList() }

    // ── Managers ──────────────────────────────────────────────────────────────
    fun saveManagers(ctx: Context, managers: List<ManagerAccess>) {
        val arr = JSONArray()
        managers.forEach { m ->
            val ids = JSONArray().also { a -> m.assignedBusinessIds.forEach { a.put(it) } }
            arr.put(JSONObject().apply {
                put("code", m.code); put("managerName", m.managerName)
                put("assignedBusinessIds", ids)
            })
        }
        pref(ctx).edit().putString(K_MANAGERS, arr.toString()).apply()
    }

    fun getManagers(ctx: Context): List<ManagerAccess> = try {
        val str = pref(ctx).getString(K_MANAGERS, null) ?: return emptyList()
        val arr = JSONArray(str)
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { j ->
                val idsArr = j.getJSONArray("assignedBusinessIds")
                ManagerAccess(j.getString("code"), j.getString("managerName"),
                    (0 until idsArr.length()).map { idsArr.getString(it) })
            }
        }
    } catch (e: Exception) { emptyList() }

    fun validateManagerCode(ctx: Context, code: String): ManagerAccess? =
        getManagers(ctx).find { it.code.equals(code.trim(), ignoreCase = true) }

    // ── Documents ─────────────────────────────────────────────────────────────
    fun saveDocs(ctx: Context, businessId: String, docs: List<DocumentItem>) {
        val all = getAllDocs(ctx).filter { it.businessId != businessId }.toMutableList()
        all.addAll(docs)
        val arr = JSONArray()
        all.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id); put("businessId", d.businessId); put("type", d.type)
                put("name", d.name); put("status", d.status); put("regNumber", d.regNumber)
                put("expiryDate", d.expiryDate); put("issueDate", d.issueDate)
            })
        }
        pref(ctx).edit().putString(K_DOCS, arr.toString()).apply()
    }

    fun getDocs(ctx: Context, businessId: String): List<DocumentItem> =
        getAllDocs(ctx).filter { it.businessId == businessId }

    private fun getAllDocs(ctx: Context): List<DocumentItem> = try {
        val str = pref(ctx).getString(K_DOCS, null) ?: return emptyList()
        val arr = JSONArray(str)
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let {
                DocumentItem(it.getString("id"), it.getString("businessId"), it.getString("type"),
                    it.getString("name"), it.getString("status"), it.optString("regNumber", ""),
                    it.optString("expiryDate", ""), it.optString("issueDate", ""))
            }
        }
    } catch (e: Exception) { emptyList() }

    fun clearAll(ctx: Context) = pref(ctx).edit().clear().apply()

    // ── ShopDetails (backward compat, delegates to first business) ─────────
    fun saveShopDetails(ctx: Context, d: ShopDetails) {
        saveBusinesses(ctx, listOf(BusinessProfile(
            name = d.name, ownerName = d.owner,
            category = d.category, scale = d.scale, state = d.state
        )))
    }

    fun getShopDetails(ctx: Context): ShopDetails? {
        val list = getBusinesses(ctx)
        if (list.isEmpty()) return null
        val b = list.first()
        return ShopDetails(b.name, b.ownerName, b.category, b.scale, b.state)
    }

    // ── Theme ───────────────────────────────────────────────────────────────
    fun saveTheme(ctx: Context, isDark: Boolean) =
        pref(ctx).edit().putBoolean(K_THEME, isDark).apply()

    fun getTheme(ctx: Context): Boolean =
        pref(ctx).getBoolean(K_THEME, true) // default to dark

    // ── Language ────────────────────────────────────────────────────────────
    fun saveLanguage(ctx: Context, code: String) =
        pref(ctx).edit().putString("language", code).apply()

    fun getLanguage(ctx: Context): String =
        pref(ctx).getString("language", "en") ?: "en"

    private fun pref(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// ─── Document Logic Helper ────────────────────────────────────────────────────

fun requiredDocMeta(category: String, scale: String): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()

    when (category) {
        // ── 1. Beauty, Salon & Personal Care ────────────────────────────────
        "Beauty, Salon & Personal Care" -> {
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("TradeLicense", "Trade License"))
            list.add(Pair("FSSAI", "FSSAI License (if food served)"))
            list.add(Pair("FireNOC", "Fire NOC (if applicable)"))
        }

        // ── 2. Marriage, Banquet & Event Services ───────────────────────────
        "Marriage, Banquet & Event Services" -> {
            list.add(Pair("TradeLicense", "Trade License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("FireNOC", "Fire NOC"))
            list.add(Pair("FSSAI", "FSSAI License (if catering)"))
            list.add(Pair("BuildingSafety", "Building Safety Certificate"))
        }

        // ── 3. Corporate Offices & Commercial Establishments ────────────────
        "Corporate Offices & Commercial Establishments" -> {
            list.add(Pair("BusinessRegistration", "Certificate of Incorporation / Business Registration"))
            list.add(Pair("PAN", "PAN Card"))
            list.add(Pair("TAN", "TAN (Tax Deduction Account Number)"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("Udyam", "MSME Registration (optional)"))
        }

        // ── 4. Banking, Finance & Insurance ─────────────────────────────────
        "Banking, Finance & Insurance" -> {
            list.add(Pair("RBI_IRDAI_SEBI_Auth", "RBI / IRDAI / SEBI Authorization"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
        }

        // ── 5. Professional & Consultancy Services ──────────────────────────
        "Professional & Consultancy Services" -> {
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("Udyam", "MSME Registration (optional)"))
        }

        // ── 6. Contractors, Builders & Developers ───────────────────────────
        "Contractors, Builders & Developers" -> {
            list.add(Pair("ContractorLicense", "Contractor License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("LabourLicense", "Labour License"))
            list.add(Pair("BuildingPermit", "Building Permit (project-based)"))
        }

        // ── 7. Labour, Security & Manpower Services ─────────────────────────
        "Labour, Security & Manpower Services" -> {
            list.add(Pair("LabourLicense", "Labour License"))
            list.add(Pair("PSARA", "PSARA License (Security Agencies)"))
            list.add(Pair("GST", "GST Registration"))
        }

        // ── 8. Courier, Logistics & Warehousing ─────────────────────────────
        "Courier, Logistics & Warehousing" -> {
            list.add(Pair("TradeLicense", "Trade License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("WarehouseRegistration", "Warehouse Registration (if applicable)"))
        }

        // ── 9. Education & Training ─────────────────────────────────────────
        "Education & Training" -> {
            list.add(Pair("InstitutionApproval", "Institution Recognition / Approval"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("FireNOC", "Fire NOC"))
        }

        // ── 10. NGO, Welfare & Research Organisations ───────────────────────
        "NGO, Welfare & Research Organisations" -> {
            list.add(Pair("TrustSocietyReg", "Trust/Society Registration"))
            list.add(Pair("PAN", "PAN Card"))
            list.add(Pair("NGO_12A_80G", "12A & 80G Registration (if applicable)"))
        }

        // ── 11. Food Retail & Grocery ───────────────────────────────────────
        "Food Retail & Grocery" -> {
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
        }

        // ── 12. Food Wholesale, Distribution & Supply ───────────────────────
        "Food Wholesale, Distribution & Supply" -> {
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("TradeLicense", "Trade License"))
        }

        // ── 13. Food Manufacturing & Processing ─────────────────────────────
        "Food Manufacturing & Processing" -> {
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("FactoryLicense", "Factory License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("PollutionControl", "Pollution Control Consent"))
        }

        // ── 14. Restaurants, Hotels & Catering ──────────────────────────────
        "Restaurants, Hotels & Catering" -> {
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("TradeLicense", "Trade License"))
            list.add(Pair("FireNOC", "Fire NOC"))
            list.add(Pair("EatingHouse", "Eating House License"))
        }

        // ── 15. Bakery, Sweets & Confectionery ──────────────────────────────
        "Bakery, Sweets & Confectionery" -> {
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("FactoryLicense", "Factory License (if manufacturing)"))
        }

        // ── 16. Beverages, Dairy & Packaged Water ───────────────────────────
        "Beverages, Dairy & Packaged Water" -> {
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("BIS", "BIS License (Packaged Water)"))
            list.add(Pair("GST", "GST Registration"))
        }

        // ── 17. Meat, Fish, Poultry & Livestock ─────────────────────────────
        "Meat, Fish, Poultry & Livestock" -> {
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("TradeLicense", "Trade License"))
            list.add(Pair("VetApproval", "Veterinary Approval (if applicable)"))
        }

        // ── 18. Fruits, Vegetables & Agricultural Produce ───────────────────
        "Fruits, Vegetables & Agricultural Produce" -> {
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
        }

        // ── 19. Agriculture Inputs & Allied Activities ──────────────────────
        "Agriculture Inputs & Allied Activities" -> {
            list.add(Pair("FertilizerLicense", "Fertilizer/Seed/Pesticide License"))
            list.add(Pair("GST", "GST Registration"))
        }

        // ── 20. Garments, Textile & Tailoring ───────────────────────────────
        "Garments, Textile & Tailoring" -> {
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("FactoryLicense", "Factory License (if manufacturing)"))
        }

        // ── 21. Jewellery, Cosmetics & Fashion Accessories ──────────────────
        "Jewellery, Cosmetics & Fashion Accessories" -> {
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("BIS_Hallmark", "BIS Hallmark Registration (Jewellery)"))
        }

        // ── 22. General Retail & Variety Stores ─────────────────────────────
        "General Retail & Variety Stores" -> {
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("TradeLicense", "Trade License"))
        }

        // ── 23. Stationery, Books, Printing & Publishing ────────────────────
        "Stationery, Books, Printing & Publishing" -> {
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("GST", "GST Registration"))
        }

        // ── 24. IT, Software & Digital Services ─────────────────────────────
        "IT, Software & Digital Services" -> {
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("DPIIT", "DPIIT Recognition (optional)"))
        }

        // ── 25. Electronics, Electrical & Telecom ───────────────────────────
        "Electronics, Electrical & Telecom" -> {
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("BIS", "BIS Registration (if manufacturing)"))
        }

        // ── 26. Repair, Maintenance & Technical Services ────────────────────
        "Repair, Maintenance & Technical Services" -> {
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("GST", "GST Registration"))
        }

        // ── 27. Healthcare, Clinics & Diagnostics ───────────────────────────
        "Healthcare, Clinics & Diagnostics" -> {
            list.add(Pair("ClinicalEstablishment", "Clinical Establishment Registration"))
            list.add(Pair("MedicalCouncil", "Medical Council Registration"))
            list.add(Pair("BioMedicalWaste", "Biomedical Waste Authorization"))
        }

        // ── 28. Pharmacy, Medicines & Medical Equipment ─────────────────────
        "Pharmacy, Medicines & Medical Equipment" -> {
            list.add(Pair("DrugLicense", "Drug License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
        }

        // ── 29. Hotels, Lodging & Hospitality ───────────────────────────────
        "Hotels, Lodging & Hospitality" -> {
            list.add(Pair("HotelLicense", "Hotel License"))
            list.add(Pair("FSSAI", "FSSAI License"))
            list.add(Pair("FireNOC", "Fire NOC"))
            list.add(Pair("GST", "GST Registration"))
        }

        // ── 30. Automobile, Transport & Travel ──────────────────────────────
        "Automobile, Transport & Travel" -> {
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("TradeLicense", "Trade License"))
            list.add(Pair("RTO_Permit", "RTO Registration / Permit"))
        }

        // ── 31. Construction Materials, Hardware & Industrial Goods ─────────
        "Construction Materials, Hardware & Industrial Goods" -> {
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("FactoryLicense", "Factory License (if manufacturing)"))
        }

        // ── 32. Manufacturing, Workshops & Industrial Activities ────────────
        "Manufacturing, Workshops & Industrial Activities" -> {
            list.add(Pair("FactoryLicense", "Factory License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("PollutionControl", "Pollution Control Consent"))
            list.add(Pair("FireNOC", "Fire NOC"))
            list.add(Pair("LabourLicense", "Labour License"))
        }

        // ── Default / catch-all ──────────────────────────────────────────────
        else -> {
            list.add(Pair("ShopEstablishment", "Shop & Establishment License"))
            list.add(Pair("GST", "GST Registration"))
            list.add(Pair("TradeLicense", "Trade License"))
            list.add(Pair("Udyam", "MSME Registration (optional but recommended)"))
        }
    }
    return list
}

fun docDescription(type: String): String = when (type) {
    "BusinessRegistration" -> "Legal entity registration — Proprietorship, Partnership, LLP or Company"
    "PAN" -> "Permanent Account Number issued by Income Tax Department"
    "ShopEstablishment" -> "Local municipal trade license for shops & commercial establishments"
    "GST" -> "Tax registration for businesses above threshold"
    "FSSAI" -> "Food Safety and Standards compliance — Food business registration"
    "FSSAI_FOOD_LICENSE" -> "Food Safety and Standards compliance — Food business registration"
    "DrugLicense" -> "Mandatory for selling, stocking or manufacturing medicines & pharma"
    "DRUG_LICENSE" -> "Mandatory for selling, stocking or manufacturing medicines & pharma"
    "HealthTrade" -> "Municipal health & hygiene permit for beauty & wellness"
    "TradeLicense" -> "Municipal trade license for specific business operations"
    "TRADE_LICENSE" -> "Municipal trade license for specific business operations"
    "FireNOC" -> "Safety clearance from fire department"
    "FIRE_SAFETY" -> "Safety clearance from fire department"
    "Udyam" -> "Government MSME registration portal (optional but recommended)"
    "MSME" -> "Government MSME registration portal (optional but recommended)"
    "MSME_CERTIFICATE" -> "MSME Udyam Registration Certificate — verified from government portal"
    "LabourLicense" -> "Registration under Contract Labour Act for hired workers"
    "LABOUR_LICENSE" -> "Registration under Contract Labour Act for hired workers"
    "FactoryLicense" -> "License for manufacturing unit under Factories Act"
    "EatingHouse" -> "License for restaurants, hotels and eating establishments"
    "PollutionControl" -> "Consent from State Pollution Control Board"
    "POLLUTION_CONTROL" -> "Consent from State Pollution Control Board"
    "ContractorLicense" -> "License for building and construction contractors"
    "BuildingPermit" -> "Approved building plan or construction permit"
    "BuildingSafety" -> "Building safety and structural stability certificate"
    "PSARA" -> "Private Security Agencies Regulation Act license"
    "WarehouseRegistration" -> "Registration for warehousing and storage facilities"
    "InstitutionApproval" -> "Recognition or approval from relevant education authority"
    "TrustSocietyReg" -> "Registration under Trust Act or Societies Registration Act"
    "NGO_12A_80G" -> "12A & 80G tax exemption registration for NGOs"
    "ClinicalEstablishment" -> "Registration under Clinical Establishments Act"
    "MedicalCouncil" -> "Registration with relevant medical council or board"
    "BioMedicalWaste" -> "Authorization for biomedical waste management"
    "HotelLicense" -> "License for hotels, lodges and hospitality establishments"
    "RTO_Permit" -> "Registration or permit from Regional Transport Office"
    "RBI_IRDAI_SEBI_Auth" -> "Authorization from RBI, IRDAI or SEBI regulatory body"
    "BIS" -> "Bureau of Indian Standards registration for manufactured goods"
    "BIS_Hallmark" -> "BIS Hallmark registration for jewellery purity certification"
    "DPIIT" -> "Department for Promotion of Industry and Internal Trade recognition"
    "FertilizerLicense" -> "License for manufacture/sale of fertilizers, seeds or pesticides"
    "VetApproval" -> "Veterinary approval for handling meat, fish or livestock"
    "TAN" -> "Tax Deduction and Collection Account Number for TDS compliance"
    "NGO_DAR" -> "NGO Darpan Registration for charitable organisations"
    "FCRA" -> "FCRA (Foreign Contribution Regulation Act) registration"
    "IEC" -> "Import Export Code for international trade"
    "TRADEMARK" -> "Trademark registration for brand protection"
    "PROFESSIONAL_TAX" -> "Professional Tax registration for business operations"
    "PROPERTY_TAX" -> "Property Tax certificate for owned premises"
    "SHOP_INSURANCE" -> "Shop insurance policy for business protection"
    else -> "Government compliance certificate"
}

fun docFetchLabel(type: String): String = when (type) {
    "GST" -> "GSTIN Number (15-char alphanumeric)"
    "FSSAI" -> "FSSAI Lic./Reg. No. (14-digit)"
    "Udyam" -> "Udyam Reg. No. (UDYAM-XX-00-0000000)"
    "DrugLicense" -> "Drug License Number (Form 20/21)"
    "PAN" -> "PAN (10-char alphanumeric)"
    "BusinessRegistration" -> "Registration / CIN Number"
    "TradeLicense" -> "Trade License Number"
    "ShopEstablishment" -> "Shop & Establishment Reg. No."
    "FireNOC" -> "NOC Reference Number"
    "LabourLicense" -> "Labour License Registration No."
    "FactoryLicense" -> "Factory License Number"
    "EatingHouse" -> "Eating House License Number"
    "PollutionControl" -> "Pollution Consent ID"
    "ContractorLicense" -> "Contractor License Number"
    "BuildingPermit" -> "Building Permit / Approval Number"
    "BuildingSafety" -> "Building Safety Certificate No."
    "PSARA" -> "PSARA License Number"
    "WarehouseRegistration" -> "Warehouse Registration ID"
    "InstitutionApproval" -> "Recognition / Approval Number"
    "TrustSocietyReg" -> "Trust / Society Registration No."
    "NGO_12A_80G" -> "12A / 80G Registration Number"
    "ClinicalEstablishment" -> "Clinical Establishment Reg. No."
    "MedicalCouncil" -> "Medical Council Registration No."
    "BioMedicalWaste" -> "Bio-Medical Waste Authorization ID"
    "HotelLicense" -> "Hotel License Number"
    "RTO_Permit" -> "RTO Reg. / Permit Number"
    "RBI_IRDAI_SEBI_Auth" -> "Regulatory Authorization Reference"
    "BIS" -> "BIS Registration / License No."
    "BIS_Hallmark" -> "BIS Hallmark Registration No."
    "DPIIT" -> "DPIIT Recognition Reference No."
    "FertilizerLicense" -> "Fertilizer/Seed/Pesticide License No."
    "VetApproval" -> "Veterinary Approval Reference"
    "TAN" -> "TAN (10-char alphanumeric, e.g. DELC12345D)"
    "NGO_DAR" -> "NGO Darpan Registration Number"
    "FCRA" -> "FCRA Registration Certificate Number"
    else -> "Registration / License Number"
}
