package com.iadv.dukaanlocker.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {

    // ── Payment Endpoints ──────────────────────────────────────

    @POST("api/payments/renew")
    suspend fun createRenewalPayment(@Body request: PaymentCreateRequest): Response<PaymentCreateResponse>

    @POST("api/payments/fssa-return")
    suspend fun createFssaReturnPayment(@Body request: FssaReturnPaymentRequest): Response<PaymentCreateResponse>

    @GET("api/payments/{id}")
    suspend fun getPayment(@Path("id") id: Long): Response<PaymentStatusResponse>

    @POST("api/payments/{id}/cancel")
    suspend fun cancelRenewalPayment(@Path("id") id: Long): Response<Unit>

    @POST("api/payments/water-testing")
    suspend fun createWaterTestingPayment(@Body request: WaterTestingPaymentRequest): Response<PaymentCreateResponse>

    @POST("api/payments/{id}/callback")
    suspend fun paymentCallback(@Path("id") id: Long, @Body body: Map<String, String>): Response<PaymentStatusResponse>

    @GET("api/payments/my")
    suspend fun getMyPayments(): Response<List<PaymentStatusResponse>>

    // ── FSSAI Compliance ──────────────────────────────────────

    @GET("api/fssa/compliance")
    suspend fun getFssaCompliance(): Response<List<FssaComplianceStatus>>

    @POST("api/fssa/{shopId}/mark-filed")
    suspend fun markFssaReturnFiled(@Path("shopId") shopId: Long): Response<Map<String, String>>

    // ── Drug License Endpoints ─────────────────────────────────

    @POST("api/drug-license/payment")
    suspend fun createDrugLicensePayment(@Body request: DrugLicensePaymentRequest): Response<PaymentCreateResponse>

    @GET("api/drug-license/verify/{licenseNumber}")
    suspend fun verifyDrugLicense(@Path("licenseNumber") licenseNumber: String): Response<DrugLicenseVerificationResponse>

    @POST("api/drug-license/fetch")
    suspend fun fetchDrugLicense(@Body request: DrugLicenseFetchRequest): Response<DrugLicenseFetchResponse>

    @GET("api/drug-license/callback/{paymentId}")
    suspend fun drugLicenseCallback(@Path("paymentId") paymentId: Long): Response<Unit>

    // ── Shop Endpoints ─────────────────────────────────────────

    @POST("api/shops")
    suspend fun createShop(@Body request: CreateShopRequest): Response<ShopResponse>

    @PUT("api/shops/{shopId}")
    suspend fun updateShop(@Path("shopId") shopId: Long, @Body request: UpdateShopRequest): Response<ShopResponse>

    @GET("api/shops/my-shops")
    suspend fun getMyShops(): Response<List<ShopResponse>>

    @GET("api/shops/{shopId}/documents")
    suspend fun getShopDocuments(@Path("shopId") shopId: Long): Response<List<DocumentResponse>>

    @Multipart
    @PUT("api/shops/{shopId}/documents/{documentType}/reupload")
    suspend fun uploadDocument(
        @Path("shopId") shopId: Long,
        @Path("documentType") documentType: String,
        @Part file: MultipartBody.Part,
        @Query("documentNumber") documentNumber: String? = null,
        @Query("issueDate") issueDate: String? = null,
        @Query("expiryDate") expiryDate: String? = null
    ): Response<DocumentResponse>

    // ── Manager Endpoints ──────────────────────────────────────

    @GET("api/managers/me/shops")
    suspend fun getMyAssignedShops(): Response<List<ShopResponse>>

    @GET("api/managers")
    suspend fun getManagers(): Response<List<ManagerResponse>>

    @GET("api/managers/{managerId}/shops")
    suspend fun getManagerShops(@Path("managerId") managerId: Long): Response<List<ShopResponse>>

    @POST("api/managers")
    suspend fun createManager(@Body request: CreateManagerRequest): Response<ManagerResponse>

    @POST("api/managers/{managerId}/shops/{shopId}")
    suspend fun assignShopToManager(@Path("managerId") managerId: Long, @Path("shopId") shopId: Long): Response<Unit>

    @PUT("api/managers/{managerId}/shops/{shopId}/deactivate")
    suspend fun deactivateAssignment(@Path("managerId") id: Long, @Path("shopId") shopId: Long): Response<Unit>

    @PUT("api/managers/{managerId}/disable")
    suspend fun disableManager(@Path("managerId") id: Long): Response<Unit>

    @PUT("api/managers/{managerId}/enable")
    suspend fun enableManager(@Path("managerId") id: Long): Response<Unit>

    // ── Auth Endpoints ─────────────────────────────────────────

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register-msme")
    suspend fun registerWithMsme(@Body request: RegisterWithMsmeRequest): Response<AuthResponse>

    @POST("api/auth/login-google")
    suspend fun loginWithGoogle(@Body request: GoogleRegisterRequest): Response<AuthResponse>

    @POST("api/auth/login-by-code")
    suspend fun loginByCode(@Body request: ManagerCodeLoginRequest): Response<AuthResponse>

    @POST("api/auth/biometric-login")
    suspend fun biometricLogin(@Body request: BiometricLoginRequest): Response<AuthResponse>

    @POST("api/auth/msme-login-request")
    suspend fun msmeLoginRequest(@Body request: MsmeOtpRequest): Response<MsmeOtpResponse>

    @POST("api/auth/msme-login-verify")
    suspend fun msmeLoginVerify(@Body request: MsmeOtpVerifyRequest): Response<AuthResponse>

    @POST("api/auth/mobile-hint")
    suspend fun mobileHint(@Body request: MobileHintRequest): Response<MobileHintResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<OtpSendResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<MessageResponse>

    // ── Business Profile ───────────────────────────────────────

    @POST("api/business-profile")
    suspend fun createOrUpdateProfile(@Body request: BusinessProfileRequest): Response<Map<String, String>>

    // ── Udyam ──────────────────────────────────────────────────

    @POST("api/udyam/init")
    suspend fun initUdyamSession(): Response<UdyamInitResponse>

    @POST("api/udyam/fetch")
    suspend fun fetchUdyam(@Body request: UdyamFetchRequest): Response<UdyamVerifyResponse>

    // ── GST ────────────────────────────────────────────────────

    @POST("api/gst/fetch")
    suspend fun fetchGst(@Body request: GstFetchRequest): Response<GstVerificationResponse>

    // ── FSSAI Verification ─────────────────────────────────────

    @POST("api/fssai/fetch")
    suspend fun fetchFssai(@Body request: FssaiFetchRequest): Response<FssaiVerificationResponse>

    // ── Renewal ────────────────────────────────────────────────

    @POST("api/renewals")
    suspend fun requestRenewal(@Body request: CreateRenewalRequest): Response<RenewalOrderItem>

    // ── Notifications ──────────────────────────────────────────

    @GET("api/notifications")
    suspend fun getNotifications(): Response<List<NotificationItem>>

    @GET("api/notifications/bin")
    suspend fun getBinNotifications(): Response<List<NotificationItem>>

    @PUT("api/notifications/mark-read")
    suspend fun markNotificationsAsRead(): Response<Unit>

    @PUT("api/notifications/move-to-bin")
    suspend fun moveToBin(): Response<Unit>

    @PUT("api/notifications/{id}/move-to-bin")
    suspend fun moveNotificationToBin(@Path("id") notificationId: Long): Response<Unit>

    @PUT("api/notifications/{id}/restore")
    suspend fun restoreFromBin(@Path("id") notificationId: Long): Response<Unit>

    @PUT("api/notifications/restore-all")
    suspend fun restoreAllFromBin(): Response<Unit>

    @DELETE("api/notifications/{id}")
    suspend fun deleteNotification(@Path("id") notificationId: Long): Response<Unit>

    @DELETE("api/notifications/permanent-delete")
    suspend fun permanentDelete(): Response<Unit>

    @DELETE("api/notifications/clear")
    suspend fun clearNotifications(): Response<Unit>

    // ── Device Token ───────────────────────────────────────────

    @POST("api/device-token")
    suspend fun registerDeviceToken(@Body body: Map<String, String>): Response<Map<String, String>>

    @DELETE("api/device-token")
    suspend fun removeDeviceToken(@Header("Authorization") authorization: String, @Body body: Map<String, String>): Response<Map<String, String>>

    // ── Location Search ────────────────────────────────────────

    @GET("api/location/search")
    suspend fun searchLocations(@Query("q") query: String): Response<LocationSearchResponse>
}
