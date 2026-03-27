package com.example.stockdecision.data.remote

import com.example.stockdecision.data.model.Stock
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Cloud API Service Interface
 * Communicates with the Node.js backend server
 */
interface CloudApiService {

    companion object {
        // Change this to your server URL when deployed
        const val BASE_URL = "http://10.0.2.2:3000/" // For Android emulator
        // const val BASE_URL = "https://your-server.com/" // Production
    }

    // ==================== User APIs ====================

    @POST("api/users/register")
    suspend fun registerUser(@Body request: RegisterUserRequest): Response<ApiResponse<UserResponse>>

    @PUT("api/users/{userId}/token")
    suspend fun updateFcmToken(
        @Path("userId") userId: String,
        @Body request: UpdateTokenRequest
    ): Response<ApiResponse<Unit>>

    @GET("api/users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): Response<ApiResponse<UserResponse>>

    // ==================== Stock APIs ====================

    @POST("api/stocks")
    suspend fun addStock(@Body request: AddStockRequest): Response<ApiResponse<StockIdResponse>>

    @GET("api/stocks/user/{userId}")
    suspend fun getStocksByUser(@Path("userId") userId: String): Response<ApiResponse<List<CloudStock>>>

    @GET("api/stocks/user/{userId}/active")
    suspend fun getActiveStocksByUser(@Path("userId") userId: String): Response<ApiResponse<List<CloudStock>>>

    @GET("api/stocks/{stockId}")
    suspend fun getStock(@Path("stockId") stockId: String): Response<ApiResponse<CloudStock>>

    @PUT("api/stocks/{stockId}")
    suspend fun updateStock(
        @Path("stockId") stockId: String,
        @Body request: UpdateStockRequest
    ): Response<ApiResponse<Unit>>

    @DELETE("api/stocks/{stockId}")
    suspend fun deleteStock(@Path("stockId") stockId: String): Response<ApiResponse<Unit>>
}

// ==================== Request/Response Data Classes ====================

data class RegisterUserRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("fcmToken") val fcmToken: String
)

data class UpdateTokenRequest(
    @SerializedName("fcmToken") val fcmToken: String
)

data class AddStockRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("buyPrice") val buyPrice: Double,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("targetReturnRate") val targetReturnRate: Double,
    @SerializedName("targetPrice") val targetPrice: Double
)

data class UpdateStockRequest(
    @SerializedName("buyPrice") val buyPrice: Double? = null,
    @SerializedName("quantity") val quantity: Double? = null,
    @SerializedName("targetReturnRate") val targetReturnRate: Double? = null,
    @SerializedName("targetPrice") val targetPrice: Double? = null
)

// ==================== Response Data Classes ====================

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null
)

data class UserResponse(
    @SerializedName("id") val id: String,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("last_active") val lastActive: String?
)

data class StockIdResponse(
    @SerializedName("id") val id: String
)

/**
 * Cloud Stock model matching server response
 */
data class CloudStock(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("buy_price") val buyPrice: Double,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("target_return_rate") val targetReturnRate: Double,
    @SerializedName("target_price") val targetPrice: Double,
    @SerializedName("is_triggered") val isTriggered: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("triggered_at") val triggeredAt: String?,
    @SerializedName("fcm_token") val fcmToken: String?
) {
    fun toLocalStock(): Stock {
        return Stock(
            id = 0, // Local ID will be auto-generated
            symbol = symbol,
            buyPrice = buyPrice,
            quantity = quantity,
            targetReturnRate = targetReturnRate,
            targetPrice = targetPrice,
            isTriggered = isTriggered == 1,
            createdAt = java.util.Date(),
            triggeredAt = triggeredAt?.let { java.util.Date() }
        )
    }
}
