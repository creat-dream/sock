package com.example.stockdecision.data.repository

import android.content.Context
import android.util.Log
import com.example.stockdecision.data.model.Stock
import com.example.stockdecision.data.remote.AddStockRequest
import com.example.stockdecision.data.remote.CloudApiService
import com.example.stockdecision.data.remote.CloudStock
import com.example.stockdecision.data.remote.RegisterUserRequest
import com.example.stockdecision.data.remote.RetrofitClient
import com.example.stockdecision.data.remote.UpdateStockRequest
import com.example.stockdecision.data.remote.UpdateTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for cloud stock operations
 * Handles synchronization between local database and cloud server
 */
class CloudStockRepository(private val context: Context) {

    companion object {
        private const val TAG = "CloudStockRepository"
        private const val PREFS_NAME = "cloud_stock_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }

    private val cloudApiService: CloudApiService = RetrofitClient.cloudApiService
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get or create unique user ID for this device
     */
    fun getUserId(): String {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        return userId
    }

    /**
     * Get stored FCM token
     */
    fun getFcmToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    /**
     * Initialize user with FCM token
     * Should be called when app starts
     */
    suspend fun initializeUser(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            
            // Get FCM token from Firebase
            val fcmToken = try {
                FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get FCM token", e)
                getFcmToken() // Fallback to stored token
            }

            if (fcmToken == null) {
                return@withContext Result.failure(Exception("FCM token not available"))
            }

            // Store token locally
            prefs.edit().putString(KEY_FCM_TOKEN, fcmToken).apply()

            // Register with server
            val response = cloudApiService.registerUser(
                RegisterUserRequest(userId, fcmToken)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "User registered successfully: $userId")
                Result.success(userId)
            } else {
                val errorMsg = response.body()?.message ?: response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Failed to register user: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing user", e)
            Result.failure(e)
        }
    }

    /**
     * Sync FCM token with server
     */
    suspend fun syncFcmToken(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            
            prefs.edit().putString(KEY_FCM_TOKEN, fcmToken).apply()

            val response = cloudApiService.updateFcmToken(
                userId,
                UpdateTokenRequest(fcmToken)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "FCM token synced successfully")
                Result.success(Unit)
            } else {
                val errorMsg = response.body()?.message ?: "Unknown error"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Add stock to cloud server
     */
    suspend fun addStock(stock: Stock): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            
            val request = AddStockRequest(
                userId = userId,
                symbol = stock.symbol,
                buyPrice = stock.buyPrice,
                quantity = stock.quantity,
                targetReturnRate = stock.targetReturnRate,
                targetPrice = stock.targetPrice
            )

            val response = cloudApiService.addStock(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val cloudStockId = response.body()?.data?.id
                Log.d(TAG, "Stock added to cloud: $cloudStockId")
                Result.success(cloudStockId!!)
            } else {
                val errorMsg = response.body()?.message ?: response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Failed to add stock: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding stock to cloud", e)
            Result.failure(e)
        }
    }

    /**
     * Get all stocks from cloud
     */
    suspend fun getStocks(): Result<List<CloudStock>> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            val response = cloudApiService.getStocksByUser(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                val stocks = response.body()?.data ?: emptyList()
                Log.d(TAG, "Retrieved ${stocks.size} stocks from cloud")
                Result.success(stocks)
            } else {
                val errorMsg = response.body()?.message ?: response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stocks from cloud", e)
            Result.failure(e)
        }
    }

    /**
     * Get active stocks from cloud
     */
    suspend fun getActiveStocks(): Result<List<CloudStock>> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            val response = cloudApiService.getActiveStocksByUser(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                val stocks = response.body()?.data ?: emptyList()
                Result.success(stocks)
            } else {
                val errorMsg = response.body()?.message ?: response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active stocks from cloud", e)
            Result.failure(e)
        }
    }

    /**
     * Update stock on cloud
     */
    suspend fun updateStock(cloudStockId: String, stock: Stock): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = UpdateStockRequest(
                buyPrice = stock.buyPrice,
                quantity = stock.quantity,
                targetReturnRate = stock.targetReturnRate,
                targetPrice = stock.targetPrice
            )

            val response = cloudApiService.updateStock(cloudStockId, request)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Stock updated on cloud: $cloudStockId")
                Result.success(Unit)
            } else {
                val errorMsg = response.body()?.message ?: response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating stock on cloud", e)
            Result.failure(e)
        }
    }

    /**
     * Delete stock from cloud
     */
    suspend fun deleteStock(cloudStockId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = cloudApiService.deleteStock(cloudStockId)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Stock deleted from cloud: $cloudStockId")
                Result.success(Unit)
            } else {
                val errorMsg = response.body()?.message ?: response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting stock from cloud", e)
            Result.failure(e)
        }
    }

    /**
     * Sync local stocks with cloud
     * This is a one-way sync: local -> cloud
     */
    suspend fun syncStocks(localStocks: List<Stock>, cloudStockIds: Map<Long, String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting stock sync...")
            
            // Get current cloud stocks
            val cloudStocksResult = getStocks()
            if (cloudStocksResult.isFailure) {
                return@withContext Result.failure(cloudStocksResult.exceptionOrNull()!!)
            }
            
            val cloudStocks = cloudStocksResult.getOrDefault(emptyList())
            val cloudStockIdsSet = cloudStocks.map { it.id }.toSet()
            
            // Add new local stocks to cloud
            for (stock in localStocks) {
                if (!cloudStockIds.containsKey(stock.id)) {
                    addStock(stock)
                }
            }
            
            // Delete cloud stocks that don't exist locally
            val localIds = localStocks.map { cloudStockIds[it.id] }.filterNotNull().toSet()
            for (cloudStock in cloudStocks) {
                if (!localIds.contains(cloudStock.id)) {
                    deleteStock(cloudStock.id)
                }
            }
            
            Log.d(TAG, "Stock sync completed")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing stocks", e)
            Result.failure(e)
        }
    }
}
