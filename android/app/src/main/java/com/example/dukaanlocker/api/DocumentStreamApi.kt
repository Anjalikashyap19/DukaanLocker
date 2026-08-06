package com.example.dukaanlocker.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * API service for secure document viewing with one-time view tokens.
 * 
 * Flow:
 * 1. Request a view token for a document ID
 * 2. Use the token to stream the document
 * 
 * Security Features:
 * - JWT authentication required
 * - One-time view tokens with 15-second TTL
 * - Document ownership/authorization check
 * - No S3 URLs exposed to client
 */
interface DocumentStreamApi {

    /**
     * Request a one-time view token to securely view a document.
     * 
     * @param request ViewDocumentRequest containing documentId
     * @return ViewTokenResponse with the one-time token
     */
    @POST("api/documents/view")
    suspend fun requestViewToken(
        @Body request: ViewDocumentRequest
    ): Response<ViewTokenResponse>

    /**
     * Stream a document using a one-time view token.
     * 
     * @param request StreamDocumentRequest containing viewToken
     * @return ResponseBody containing the document binary data
     */
    @POST("api/documents/stream")
    suspend fun streamDocument(
        @Body request: StreamDocumentRequest
    ): Response<ResponseBody>
}

/**
 * Request DTO for requesting a view token.
 */
data class ViewDocumentRequest(
    val documentId: Long
)

/**
 * Response DTO containing the one-time view token.
 */
data class ViewTokenResponse(
    val viewToken: String,
    val documentId: Long,
    val fileName: String?,
    val expiresIn: Int
)

/**
 * Request DTO for streaming a document.
 */
data class StreamDocumentRequest(
    val viewToken: String
)
