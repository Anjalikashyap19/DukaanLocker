package com.example.dukaanlocker.api

import com.google.gson.annotations.SerializedName

// ── Authentication ───────────────────────────────────────────────────────────

data class RegisterRequest(
    @SerializedName("userName") val userName: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("emailId") val emailId: String,
    @SerializedName("password") val password: String
)

data class LoginRequest(
    @SerializedName("emailId") val emailId: String,
    @SerializedName("password") val password: String
)

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("tokenType") val tokenType: String,
    @SerializedName("userId") val userId: Long,
    @SerializedName("userName") val userName: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("emailId") val emailId: String,
    @SerializedName("role") val role: String
)

// ── Shops (Businesses) ──────────────────────────────────────────────────────

data class CreateShopRequest(
    @SerializedName("shopName") val shopName: String,
    @SerializedName("ownerName") val ownerName: String,
    @SerializedName("mobile") val mobile: String,
    @SerializedName("category") val category: String,
    @SerializedName("scale") val scale: String,
    @SerializedName("state") val state: String,
    @SerializedName("city") val city: String,
    @SerializedName("branchName") val branchName: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("pincode") val pincode: String? = null
)

data class UpdateShopRequest(
    @SerializedName("shopName") val shopName: String? = null,
    @SerializedName("ownerName") val ownerName: String? = null,
    @SerializedName("mobile") val mobile: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("scale") val scale: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("branchName") val branchName: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("pincode") val pincode: String? = null
)

data class ShopResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("shopName") val shopName: String,
    @SerializedName("ownerName") val ownerName: String,
    @SerializedName("mobile") val mobile: String,
    @SerializedName("category") val category: String,
    @SerializedName("scale") val scale: String,
    @SerializedName("state") val state: String,
    @SerializedName("city") val city: String,
    @SerializedName("branchName") val branchName: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("pincode") val pincode: String?,
    @SerializedName("ownerUserId") val ownerUserId: Long,
    @SerializedName("ownerEmail") val ownerEmail: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ── Documents ────────────────────────────────────────────────────────────────

data class DocumentResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("shopId") val shopId: Long,
    @SerializedName("documentType") val documentType: String,
    @SerializedName("fileName") val fileName: String?,
    @SerializedName("fileUrl") val fileUrl: String?,
    @SerializedName("documentNumber") val documentNumber: String?,
    @SerializedName("issueDate") val issueDate: String?,
    @SerializedName("expiryDate") val expiryDate: String?,
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: Int,
    @SerializedName("uploadedAt") val uploadedAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ── Managers ─────────────────────────────────────────────────────────────────

data class CreateManagerRequest(
    @SerializedName("userName") val userName: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("emailId") val emailId: String,
    @SerializedName("password") val password: String
)

data class ManagerResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("userName") val userName: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("emailId") val emailId: String,
    @SerializedName("role") val role: String,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("createdByAdminId") val createdByAdminId: Long?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ── Business Profile ─────────────────────────────────────────────────────────

data class BusinessProfileRequest(
    @SerializedName("businessCount") val businessCount: String,
    @SerializedName("crossCategory") val crossCategory: Boolean = false,
    @SerializedName("multipleBranches") val multipleBranches: Boolean = false,
    @SerializedName("operationScope") val operationScope: String,
    @SerializedName("businessPresence") val businessPresence: String
)

data class BusinessProfileResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("userId") val userId: Long,
    @SerializedName("businessCount") val businessCount: String,
    @SerializedName("crossCategory") val crossCategory: Boolean,
    @SerializedName("multipleBranches") val multipleBranches: Boolean,
    @SerializedName("operationScope") val operationScope: String,
    @SerializedName("businessPresence") val businessPresence: String,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ── Location Search ──────────────────────────────────────────────────────────

data class LocationSearchResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("suggestions") val suggestions: List<LocationSuggestion>?
)

data class LocationSuggestion(
    @SerializedName("displayAddress") val displayAddress: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

// ── Error Response (matches backend FssaiErrorResponse) ──────────────────────

data class ErrorResponse(
    @SerializedName("status") val status: Int = 0,
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("details") val details: List<String>? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

// ── Udyam (MSME) Verification ──────────────────────────────────────────

data class UdyamInitResponse(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("captchaBase64") val captchaBase64: String,
    @SerializedName("message") val message: String?
)

data class UdyamVerifyRequest(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("udyamNumber") val udyamNumber: String,
    @SerializedName("captchaText") val captchaText: String
)

data class UdyamVerifyResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("pdfUrl") val pdfUrl: String?,
    @SerializedName("certificateHtml") val certificateHtml: String?,
    @SerializedName("udyamNumber") val udyamNumber: String?,
    @SerializedName("errorMessage") val errorMessage: String?
)

data class RegisterWithMsmeRequest(
    @SerializedName("msmeNumber") val msmeNumber: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("password") val password: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("captchaText") val captchaText: String
)

data class MsmeAuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("tokenType") val tokenType: String,
    @SerializedName("userId") val userId: Long,
    @SerializedName("userName") val userName: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("emailId") val emailId: String,
    @SerializedName("role") val role: String,
    @SerializedName("certificatePdfUrl") val certificatePdfUrl: String?,
    @SerializedName("udyamNumber") val udyamNumber: String?
)

fun <T> retrofit2.Response<T>.parseErrorMessage(): String {
    val body = errorBody()?.string()
    if (body != null) {
        try {
            val gson = com.google.gson.Gson()
            val err = gson.fromJson(body, ErrorResponse::class.java)
            if (!err.message.isNullOrBlank()) return err.message
        } catch (_: Exception) {}
    }
    return message()
}
