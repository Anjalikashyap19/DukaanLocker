package com.example.dukaanlocker.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Authentication ───────────────────────────────────────────────────────

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    // ── Shops ────────────────────────────────────────────────────────────────

    @POST("api/shops")
    suspend fun createShop(@Body request: CreateShopRequest): Response<ShopResponse>

    @GET("api/shops/my-shops")
    suspend fun getMyShops(): Response<List<ShopResponse>>

    @GET("api/shops/{shopId}")
    suspend fun getShop(@Path("shopId") shopId: Long): Response<ShopResponse>

    @PUT("api/shops/{shopId}")
    suspend fun updateShop(@Path("shopId") shopId: Long, @Body request: UpdateShopRequest): Response<ShopResponse>

    // ── Documents ────────────────────────────────────────────────────────────

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

    // ── Managers ─────────────────────────────────────────────────────────────

    @POST("api/managers")
    suspend fun createManager(@Body request: CreateManagerRequest): Response<ManagerResponse>

    @GET("api/managers")
    suspend fun getManagers(): Response<List<ManagerResponse>>

    @POST("api/managers/{managerId}/shops/{shopId}")
    suspend fun assignShopToManager(
        @Path("managerId") managerId: Long,
        @Path("shopId") shopId: Long
    ): Response<ManagerResponse>

    @GET("api/managers/{managerId}/shops")
    suspend fun getManagerShops(@Path("managerId") managerId: Long): Response<List<ShopResponse>>

    @GET("api/managers/me/shops")
    suspend fun getMyAssignedShops(): Response<List<ShopResponse>>

    @PUT("api/managers/{managerId}/shops/{shopId}/deactivate")
    suspend fun deactivateAssignment(
        @Path("managerId") managerId: Long,
        @Path("shopId") shopId: Long
    ): Response<Unit>

    // ── Business Profile ─────────────────────────────────────────────────────

    @POST("api/business-profile")
    suspend fun createOrUpdateProfile(@Body request: BusinessProfileRequest): Response<BusinessProfileResponse>

    @GET("api/business-profile")
    suspend fun getMyProfile(): Response<BusinessProfileResponse>

    // ── Location Search ──────────────────────────────────────────────────────

    @GET("api/location/search")
    suspend fun searchLocations(@Query("query") query: String): Response<LocationSearchResponse>
}
