package com.example.stockdecision.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.stockdecision.data.model.Stock
import com.example.stockdecision.ui.screens.ApiKeyScreen
import com.example.stockdecision.ui.screens.MainScreen
import com.example.stockdecision.ui.screens.StockDetailScreen
import com.example.stockdecision.ui.theme.StockDecisionTheme
import com.example.stockdecision.ui.viewmodel.ApiKeyViewModel
import com.example.stockdecision.ui.viewmodel.StockViewModel

/**
 * Main Activity with Navigation
 */
class MainActivity : ComponentActivity() {
    
    private val stockViewModel: StockViewModel by viewModels()
    private val apiKeyViewModel: ApiKeyViewModel by viewModels()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle permission result if needed
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale if needed
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        
        setContent {
            StockDecisionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    StockDecisionApp(
                        navController = navController,
                        stockViewModel = stockViewModel,
                        apiKeyViewModel = apiKeyViewModel
                    )
                }
            }
        }
    }
}

/**
 * Main App Navigation
 */
@Composable
fun StockDecisionApp(
    navController: NavHostController,
    stockViewModel: StockViewModel,
    apiKeyViewModel: ApiKeyViewModel
) {
    val stockUiState by stockViewModel.uiState.collectAsState()
    val stockDetailUiState by stockViewModel.detailUiState.collectAsState()
    val currentPrices by stockViewModel.currentPrices.collectAsState()
    val apiKeyUiState by apiKeyViewModel.uiState.collectAsState()
    
    var selectedStock by remember { mutableStateOf<Stock?>(null) }
    
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                uiState = stockUiState,
                currentPrices = currentPrices,
                onAddStock = { stock ->
                    stockViewModel.addStock(stock)
                },
                onDeleteStock = { stock ->
                    stockViewModel.deleteStock(stock)
                },
                onStockClick = { stock ->
                    selectedStock = stock
                    stockViewModel.loadStockDetail(stock)
                    navController.navigate("detail")
                },
                onNavigateToApiKey = {
                    navController.navigate("api_key")
                }
            )
        }
        
        composable("detail") {
            selectedStock?.let { stock ->
                StockDetailScreen(
                    stock = stock,
                    currentPrice = currentPrices[stock.symbol.uppercase()],
                    statistics = stockDetailUiState.statistics,
                    news = stockDetailUiState.news,
                    aiPrediction = stockDetailUiState.aiPrediction,
                    aiRecommendations = stockDetailUiState.aiRecommendations,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
        
        composable("api_key") {
            ApiKeyScreen(
                uiState = apiKeyUiState,
                onApiKeyChange = { apiKey ->
                    apiKeyViewModel.updateApiKeyInput(apiKey)
                },
                onToggleVisibility = {
                    apiKeyViewModel.toggleVisibility()
                },
                onSave = {
                    apiKeyViewModel.saveApiKey()
                },
                onDelete = {
                    apiKeyViewModel.deleteApiKey()
                },
                onValidate = {
                    apiKeyViewModel.validateApiKey()
                },
                onBack = {
                    navController.popBackStack()
                },
                onClearMessages = {
                    apiKeyViewModel.clearMessages()
                }
            )
            
            // Handle save/delete success
            LaunchedEffect(apiKeyUiState.saveSuccess, apiKeyUiState.deleteSuccess) {
                if (apiKeyUiState.saveSuccess || apiKeyUiState.deleteSuccess) {
                    apiKeyViewModel.resetSuccessFlags()
                    // Optionally navigate back after success
                    // navController.popBackStack()
                }
            }
        }
    }
}
