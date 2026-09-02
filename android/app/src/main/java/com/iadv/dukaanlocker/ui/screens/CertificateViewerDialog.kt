package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

// ── Dialog: Certificate Viewer ───────────────────────────────────────────────
@Composable
fun CertificateViewerDialog(
    doc: DocumentItem,
    business: BusinessProfile,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.background),
            border = BorderStroke(2.dp, colors.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                // Header with government branding
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Ashoka Chakra symbol
                    Text("☸", fontSize = 32.sp, color = colors.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(AppStrings.get(lang, "GOVERNMENT OF INDIA"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary, letterSpacing = 2.sp)
                    Text(
                        when (doc.type) {
                            "GST" -> "DEPARTMENT OF REVENUE • GOODS AND SERVICES TAX"
                            "FSSAI" -> "FOOD SAFETY AND STANDARDS AUTHORITY OF INDIA"
                            "ShopEstablishment" -> "DEPARTMENT OF LABOUR & TRADE COMPLIANCE"
                            "DrugLicense" -> "DRUG CONTROL ADMINISTRATION"
                            "HealthTrade" -> "MUNICIPAL CORPORATION REGULATORY DEPT"
                            "FireNOC" -> "STATE FIRE AND EMERGENCY SERVICES"
                            "Udyam", "MSME_CERTIFICATE" -> "MINISTRY OF MICRO, SMALL & MEDIUM ENTERPRISES"
                            "PAN" -> "INCOME TAX DEPARTMENT • GOVERNMENT OF INDIA"
                            "TAN" -> "INCOME TAX DEPARTMENT • TDS WING"
                            "BusinessRegistration" -> "MINISTRY OF CORPORATE AFFAIRS • ROC"
                            "TradeLicense" -> "MUNICIPAL CORPORATION • COMMERCIAL TAXES"
                            "LabourLicense" -> "DEPARTMENT OF LABOUR & EMPLOYMENT"
                            "FactoryLicense" -> "CHIEF INSPECTOR OF FACTORIES"
                            "EatingHouse" -> "FOOD SAFETY & MUNICIPAL CORPORATION"
                            "PollutionControl" -> "STATE POLLUTION CONTROL BOARD"
                            "ContractorLicense" -> "STATE CONTRACTOR LICENSING AUTHORITY"
                            "BuildingPermit" -> "URBAN DEVELOPMENT AUTHORITY"
                            "BuildingSafety" -> "MUNICIPAL BUILDING SAFETY DEPT"
                            "PSARA" -> "HOME DEPARTMENT • PRIVATE SECURITY"
                            "WarehouseRegistration" -> "FOOD CORPORATION OF INDIA / STATE WAREHOUSE"
                            "InstitutionApproval" -> "EDUCATION DEPARTMENT / UGC / AICTE"
                            "TrustSocietyReg" -> "REGISTRAR OF SOCIETIES / TRUST ACT"
                            "NGO_12A_80G" -> "INCOME TAX DEPARTMENT • EXEMPTIONS"
                            "ClinicalEstablishment" -> "STATE CLINICAL ESTABLISHMENTS AUTHORITY"
                            "MedicalCouncil" -> "MEDICAL COUNCIL OF INDIA / STATE COUNCIL"
                            "BioMedicalWaste" -> "CENTRAL POLLUTION CONTROL BOARD"
                            "HotelLicense" -> "TOURISM DEPARTMENT / MUNICIPAL CORP"
                            "RTO_Permit" -> "REGIONAL TRANSPORT OFFICE"
                            "RBI_IRDAI_SEBI_Auth" -> "RBI / IRDAI / SEBI REGULATORY AUTHORITY"
                            "BIS" -> "BUREAU OF INDIAN STANDARDS"
                            "BIS_Hallmark" -> "BUREAU OF INDIAN STANDARDS • HALLMARK"
                            "DPIIT" -> "DPIIT • MINISTRY OF COMMERCE & INDUSTRY"
                            "FertilizerLicense" -> "DEPARTMENT OF AGRICULTURE / FERTILIZER DIVISION"
                            "VetApproval" -> "DEPARTMENT OF ANIMAL HUSBANDRY"
                            "NGO_DAR" -> "NGO DARPAN • NITI AAYOG"
                            "FCRA" -> "MINISTRY OF HOME AFFAIRS • FCRA WING"
                            else -> "OFFICIAL REGULATORY DEPARTMENT"
                        },
                         fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = colors.primary, thickness = 2.dp, modifier = Modifier.width(180.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Certificate title
                    Text(doc.name.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = colors.primary, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Registration number highlighted
                     Text(doc.regNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Certificate fields based on document type
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // For MSME/Udyam certificates, show specialized fields
                    if (doc.type == "Udyam" || doc.type == "MSME_CERTIFICATE") {
                        CertificateField(AppStrings.get(lang, "Udyam Registration Number"), doc.regNumber, isHighlight = true)
                        CertificateField(AppStrings.get(lang, "Name of Enterprise"), business.name.uppercase())
                        CertificateField(AppStrings.get(lang, "Name of Entrepreneur/Owner"), business.ownerName)
                        CertificateField(AppStrings.get(lang, "Type of Enterprise"), business.scale)
                        CertificateField(AppStrings.get(lang, "Major Activity"), business.category)
                        CertificateField(AppStrings.get(lang, "State of Registration"), business.state)
                        if (business.city.isNotBlank()) {
                            CertificateField(AppStrings.get(lang, "District / City"), business.city)
                        }
                        CertificateField(AppStrings.get(lang, "Issue Date"), doc.issueDate)
                        CertificateField(AppStrings.get(lang, "Validity"), AppStrings.get(lang, "Permanent (No Expiry)"))
                        
                        // Investment & Turnover section (if available)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Investment box
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(AppStrings.get(lang, "INVESTMENT"), fontSize = 8.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                                    Text(AppStrings.get(lang, "Not Available"), fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            // Turnover box
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(AppStrings.get(lang, "TURNOVER"), fontSize = 8.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                                    Text(AppStrings.get(lang, "Not Available"), fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        // QR Code placeholder
                        Spacer(modifier = Modifier.height(8.dp))
                         Card(
                             modifier = Modifier.fillMaxWidth(),
                             colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                             border = BorderStroke(1.dp, colors.border),
                             shape = RoundedCornerShape(8.dp)
                         ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("▣", fontSize = 28.sp, color = colors.primary)
                                Text(AppStrings.get(lang, "Scan to verify on Udyam Portal"), fontSize = 9.sp, color = colors.textSecondary, fontStyle = FontStyle.Italic)
                            }
                        }
                    } else {
                        // Generic certificate fields for other document types
                        CertificateField(AppStrings.get(lang, "Registration / License No"), doc.regNumber, isHighlight = true)
                        CertificateField(AppStrings.get(lang, "Legal Name of Business"), business.name.uppercase())
                        CertificateField(AppStrings.get(lang, "Name of Proprietor/Owner"), business.ownerName)
                        CertificateField(AppStrings.get(lang, "State of Registration"), business.state)
                        CertificateField(AppStrings.get(lang, "Category & Scale"), "${business.category} (${business.scale})")
                        CertificateField(AppStrings.get(lang, "Issue Date"), doc.issueDate)
                        CertificateField(AppStrings.get(lang, "Validity / Expiry Date"), doc.expiryDate)
                    }
                    
                    // Locker Status (common for all)
                    CertificateField(AppStrings.get(lang, "Locker Status"), if (doc.status == "FETCHED") AppStrings.get(lang, "VERIFIED GOVERNMENT DATA") else AppStrings.get(lang, "SECURE USER UPLOADS"),
                        textColor = if (doc.status == "FETCHED") colors.success else colors.secondary)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.background, contentColor = colors.textOnPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(AppStrings.get(lang, "Close Document View"), fontWeight = FontWeight.Bold) }
            }
        }
    }
}
