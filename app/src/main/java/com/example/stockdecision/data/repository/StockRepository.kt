package com.example.stockdecision.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.stockdecision.data.local.StockDao
import com.example.stockdecision.data.local.StockDatabase
import com.example.stockdecision.data.model.HistoricalPrice
import com.example.stockdecision.data.model.Stock
import com.example.stockdecision.data.model.StockPrice
import com.example.stockdecision.data.model.StockStatistics
import com.example.stockdecision.data.remote.ApiResult
import com.example.stockdecision.data.remote.RetrofitClient
import com.example.stockdecision.data.remote.dto.TimeSeriesDailyData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Repository for stock data operations
 * Handles both local database and remote cloud API calls
 */
class StockRepository(
    private val stockDao: StockDao,
    private val context: Context
) {
    private val apiService = RetrofitClient.stockApiService
    private val cloudRepository = CloudStockRepository(context)
    
    // Shared preferences for storing cloud stock ID mappings
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "stock_cloud_mapping", Context.MODE_PRIVATE
    )
    
    // Get API Key from database dynamically
    private suspend fun getApiKey(): String? {
        val database = StockDatabase.getDatabase(context)
        return database.apiKeyConfigDao().getApiKey()
    }
    
    // In-memory cache for prices (valid for 1 minute)
    private val priceCache = mutableMapOf<String, Pair<StockPrice, Long>>()
    private val CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(1)
    
    // Date formatter for API responses
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    companion object {
        private const val TAG = "StockRepository"
    }
    
    /**
     * Get cloud stock ID for local stock
     */
    fun getCloudStockId(localId: Long): String? {
        return prefs.getString("cloud_id_$localId", null)
    }
    
    /**
     * Save cloud stock ID mapping
     */
    private fun saveCloudStockId(localId: Long, cloudId: String) {
        prefs.edit().putString("cloud_id_$localId", cloudId).apply()
    }
    
    /**
     * Remove cloud stock ID mapping
     */
    private fun removeCloudStockId(localId: Long) {
        prefs.edit().remove("cloud_id_$localId").apply()
    }
    
    /**
     * Initialize cloud connection
     */
    suspend fun initializeCloud(): Result<String> {
        return cloudRepository.initializeUser()
    }
    
    /**
     * Get all stocks as Flow
     */
    fun getAllStocks(): Flow<List<Stock>> = stockDao.getAllStocks()
    
    /**
     * Get active (non-triggered) stocks as Flow
     */
    fun getActiveStocks(): Flow<List<Stock>> = stockDao.getActiveStocks()
    
    /**
     * Get active stocks synchronously
     */
    suspend fun getActiveStocksSync(): List<Stock> = stockDao.getActiveStocksSync()
    
    /**
     * Add a new stock to monitor (local + cloud)
     */
    suspend fun addStock(stock: Stock): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Check if already monitoring this symbol
            if (stockDao.isStockBeingMonitored(stock.symbol)) {
                return@withContext Result.failure(Exception("该股票已在监控列表中"))
            }
            
            // Add to local database first
            val localId = stockDao.insertStock(stock)
            
            // Sync to cloud
            try {
                val cloudResult = cloudRepository.addStock(stock)
                if (cloudResult.isSuccess) {
                    saveCloudStockId(localId, cloudResult.getOrThrow())
                    Log.d(TAG, "Stock synced to cloud: localId=$localId")
                } else {
                    Log.w(TAG, "Failed to sync stock to cloud: ${cloudResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud sync failed, stock saved locally only", e)
            }
            
            Result.success(localId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a stock (local + cloud)
     */
    suspend fun deleteStock(stock: Stock) = withContext(Dispatchers.IO) {
        // Delete from cloud first
        val cloudId = getCloudStockId(stock.id)
        if (cloudId != null) {
            try {
                cloudRepository.deleteStock(cloudId)
                removeCloudStockId(stock.id)
                Log.d(TAG, "Stock deleted from cloud: $cloudId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete stock from cloud", e)
            }
        }
        
        // Delete from local database
        stockDao.deleteStock(stock)
    }
    
    /**
     * Delete a stock by ID (local + cloud)
     */
    suspend fun deleteStockById(id: Long) = withContext(Dispatchers.IO) {
        // Delete from cloud first
        val cloudId = getCloudStockId(id)
        if (cloudId != null) {
            try {
                cloudRepository.deleteStock(cloudId)
                removeCloudStockId(id)
                Log.d(TAG, "Stock deleted from cloud: $cloudId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete stock from cloud", e)
            }
        }
        
        // Delete from local database
        stockDao.deleteStockById(id)
    }
    
    /**
     * Mark a stock as triggered (local only - cloud is updated by server)
     */
    suspend fun markAsTriggered(id: Long) = withContext(Dispatchers.IO) {
        stockDao.markAsTriggered(id)
        // Cloud is updated by the monitoring server when it sends the FCM notification
    }
    
    /**
     * Sync all local stocks to cloud
     * Call this when app starts or when user pulls to refresh
     */
    suspend fun syncStocksToCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val localStocks = stockDao.getActiveStocksSync()
            val cloudIdMap = localStocks.associate { it.id to getCloudStockId(it.id) }
            
            cloudRepository.syncStocks(localStocks, cloudIdMap)
            
            // Update mappings for newly synced stocks
            val cloudStocksResult = cloudRepository.getStocks()
            if (cloudStocksResult.isSuccess) {
                val cloudStocks = cloudStocksResult.getOrThrow()
                for (cloudStock in cloudStocks) {
                    val localStock = localStocks.find { it.symbol == cloudStock.symbol }
                    if (localStock != null && getCloudStockId(localStock.id) == null) {
                        saveCloudStockId(localStock.id, cloudStock.id)
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync stocks to cloud", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get current price for a symbol (with caching)
     */
    suspend fun getCurrentPrice(symbol: String): ApiResult<StockPrice> = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = priceCache[symbol.uppercase()]
        if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_DURATION_MS) {
            return@withContext ApiResult.Success(cached.first)
        }
        
        // Get API Key dynamically
        val apiKey = getApiKey()
        if (apiKey == null) {
            return@withContext ApiResult.Error("请先配置 Alpha Vantage API Key")
        }
        
        try {
            val response = apiService.getGlobalQuote(symbol = symbol, apiKey = apiKey)
            
            if (response.isSuccessful) {
                val quote = response.body()?.globalQuote
                if (quote != null && quote.symbol != null) {
                    val price = quote.price?.toDoubleOrNull() ?: 0.0
                    val change = quote.change?.toDoubleOrNull() ?: 0.0
                    val changePercent = quote.changePercent
                        ?.replace("%", "")
                        ?.toDoubleOrNull() ?: 0.0
                    
                    val stockPrice = StockPrice(
                        symbol = quote.symbol,
                        price = price,
                        change = change,
                        changePercent = changePercent
                    )
                    
                    // Update cache
                    priceCache[symbol.uppercase()] = Pair(stockPrice, System.currentTimeMillis())
                    
                    ApiResult.Success(stockPrice)
                } else {
                    ApiResult.Error("无法获取股票价格数据")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                ApiResult.Error("API 错误: ${errorBody ?: response.message()}", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error("网络错误: ${e.message}")
        }
    }
    
    /**
     * Get historical data and statistics for a symbol
     */
    suspend fun getStockStatistics(symbol: String): ApiResult<StockStatistics> = withContext(Dispatchers.IO) {
        // Get API Key dynamically
        val apiKey = getApiKey()
        if (apiKey == null) {
            return@withContext ApiResult.Error("请先配置 Alpha Vantage API Key")
        }
        
        try {
            val response = apiService.getTimeSeriesDaily(
                symbol = symbol,
                apiKey = apiKey,
                outputSize = "full"
            )
            
            if (response.isSuccessful) {
                val timeSeries = response.body()?.timeSeries
                if (timeSeries != null && timeSeries.isNotEmpty()) {
                    val historicalPrices = timeSeries.map { (dateStr, data) ->
                        parseHistoricalPrice(dateStr, data)
                    }.sortedBy { it.date }
                    
                    // Calculate statistics
                    val allPrices = historicalPrices.map { it.close }
                    val historicalHigh = allPrices.maxOrNull() ?: 0.0
                    val historicalLow = allPrices.minOrNull() ?: 0.0
                    
                    // Calculate 3-month stats
                    val threeMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.time
                    val recentPrices = historicalPrices
                        .filter { it.date.after(threeMonthsAgo) }
                        .map { it.close }
                    
                    val threeMonthHigh = recentPrices.maxOrNull() ?: 0.0
                    val threeMonthLow = recentPrices.minOrNull() ?: 0.0
                    
                    ApiResult.Success(
                        StockStatistics(
                            historicalHigh = historicalHigh,
                            historicalLow = historicalLow,
                            threeMonthHigh = threeMonthHigh,
                            threeMonthLow = threeMonthLow,
                            historicalPrices = historicalPrices
                        )
                    )
                } else {
                    ApiResult.Error("无历史数据")
                }
            } else {
                ApiResult.Error("API 错误: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error("网络错误: ${e.message}")
        }
    }
    
    /**
     * Get the count of active stocks
     */
    suspend fun getActiveStockCount(): Int = withContext(Dispatchers.IO) {
        stockDao.getActiveStockCount()
    }
    
    /**
     * Clear price cache
     */
    fun clearPriceCache() {
        priceCache.clear()
    }
    
    private fun parseHistoricalPrice(dateStr: String, data: TimeSeriesDailyData): HistoricalPrice {
        return HistoricalPrice(
            date = dateFormat.parse(dateStr) ?: Date(),
            open = data.open?.toDoubleOrNull() ?: 0.0,
            high = data.high?.toDoubleOrNull() ?: 0.0,
            low = data.low?.toDoubleOrNull() ?: 0.0,
            close = data.close?.toDoubleOrNull() ?: 0.0,
            volume = data.volume?.toLongOrNull() ?: 0
        )
    }
}
