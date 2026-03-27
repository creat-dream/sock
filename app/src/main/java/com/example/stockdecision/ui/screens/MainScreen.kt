package com.example.stockdecision.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.stockdecision.R
import com.example.stockdecision.data.model.Stock
import com.example.stockdecision.data.model.StockPrice
import com.example.stockdecision.ui.screens.components.StockInputForm
import com.example.stockdecision.ui.screens.components.StockListItem
import com.example.stockdecision.ui.viewmodel.StockUiState

/**
 * Main screen with stock input form and monitoring list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: StockUiState,
    currentPrices: Map<String, StockPrice>,
    onAddStock: (Stock) -> Unit,
    onDeleteStock: (Stock) -> Unit,
    onStockClick: (Stock) -> Unit,
    onNavigateToApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToApiKey) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Key 管理"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Input Form
            StockInputForm(
                onAddStock = onAddStock,
                modifier = Modifier.fillMaxWidth()
            )
            
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            
            // Stock List
            Text(
                text = stringResource(R.string.monitoring_list),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            if (uiState.stocks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_stocks),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.stocks, key = { it.id }) { stock ->
                        StockListItem(
                            stock = stock,
                            currentPrice = currentPrices[stock.symbol.uppercase()],
                            onDelete = { onDeleteStock(stock) },
                            onClick = { onStockClick(stock) }
                        )
                    }
                }
            }
            
            // Error message
            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { /* Dismiss */ }) {
                            Text("确定")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}
