package com.iadv.dukaanlocker.api

import com.iadv.dukaanlocker.model.request.WaterTestingPaymentRequest
import com.iadv.dukaanlocker.model.response.PaymentCreateResponse
import com.iadv.dukaanlocker.model.response.PaymentStatusResponse
import com.iadv.dukaanlocker.model.request.DrugLicensePaymentRequest
import com.iadv.dukaanlocker.model.response.DrugLicenseFetchResponse
import kotlinx.coroutines.suspendable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ── Existing Payment Endpoints ──────────────────────────────────────

@POST("api/payments/renew")
suspend fun createRenewalPayment(@Body request: PaymentCreateRequest): Response<PaymentCreateResponse>

@POST("api/payments/fssa-return")
suspend fun createFssaReturnPayment(@Body request: FssaReturnPaymentRequest): Response<PaymentCreateResponse>

@GET("api/payments/{id}")
suspend fun getPayment(@Path("id") id: Long): Response<PaymentStatusResponse>

@POST("api/payments/{id}/cancel")
suspend fun cancelRenewalPayment(@Path("id") id: Long): Response<Void>

@POST("api/payments/water-testing")
suspend fun createWaterTestingPayment(@Body request: WaterTestingPaymentRequest): Response<PaymentCreateResponse>

@POST("api/payments/{id}/callback")
suspend fun paymentCallback(@Path("id") id: Long, @Body body: Map<String, String>): Response<PaymentStatusResponse>

@GET("api/payments/my")
suspend fun getMyPayments(): Response<List<PaymentStatusResponse>>

// ── FSSAI Compliance ────────────────────────────────────────────────

@GET("api/fssa/compliance")
suspend fun getFssaCompliance(): Response<List<FssaComplianceStatus>}

@POST("api/fssa/{shopId}/mark-filed")
suspend fun markFssaReturnFiled(@Path("shopId") shopId: Long): Response<Map<String, String>}

// ── DRUG LICENSE ENDPOINTS ─────────────────────────────────────────

/**
 * Creates a drug license payment via the backend.
 * Returns the Easebuzz checkout URL and access key for the mobile SDK.
 */
@POST("api/drug-license/payment")
suspend fun createDrugLicensePayment(@Body request: DrugLicensePaymentRequest): Response<PaymentCreateResponse>

@GET("api/drug-license/verify/{licenseNumber}")
suspend fun verifyDrugLicense(@Path("licenseNumber") licenseNumber: String): Response<DrugLicenseVerificationResponse>

/**
 * Fetches and persists a drug license for a shop.
 * Verifies the license and persists it as the shop's DRUG_LICENSE document.
 */
@POST("api/drug-license/fetch")
suspend fun fetchDrugLicense(@Body request: DrugLicenseFetchRequest): Response<DrugLicenseFetchResponse>

/**
 * Consumes a pending drug license notification route.
 */
@GET("api/drug-license/callback/{paymentId}")
suspend fun drugLicenseCallback(@Path("paymentId") paymentId: Long): Response<Void>

// ── Existing Notification/Payment Routes ────────────────────────────

@POST("api/payments/{id}/cancel")
suspend fun cancelPayment(@Path("id") id: Long): Response<Void>

@GET("api/payments/my")
suspend fun getMyPayments(): Response<List<PaymentStatusResponse>>