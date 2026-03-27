const cron = require('node-cron');
const Stock = require('../models/Stock');
const AlphaVantageService = require('../services/alphaVantage');
const FcmService = require('../services/fcmService');

// Track processed stocks to avoid duplicate checks within the same minute
const processedStocks = new Map();

async function checkStockPrice(stock) {
    const cacheKey = `${stock.id}_${Date.now()}`;
    
    try {
        console.log(`Checking price for ${stock.symbol}...`);
        
        const priceData = await AlphaVantageService.getCurrentPrice(stock.symbol);
        const currentPrice = priceData.price;
        
        console.log(`${stock.symbol}: Current price = $${currentPrice}, Buy price = $${stock.buy_price}`);

        // Check if should trigger
        const returnRate = stock.buy_price > 0 
            ? ((currentPrice - stock.buy_price) / stock.buy_price) * 100 
            : 0;
        
        const shouldTrigger = returnRate >= stock.target_return_rate || currentPrice >= stock.target_price;

        if (shouldTrigger) {
            console.log(`🚨 ALERT: ${stock.symbol} triggered! Price: $${currentPrice}, Return: ${returnRate.toFixed(2)}%`);
            
            // Send FCM notification
            if (stock.fcm_token) {
                const result = await FcmService.sendPriceAlert(stock.fcm_token, stock, currentPrice);
                
                if (result.success) {
                    console.log(`✅ FCM notification sent for ${stock.symbol}`);
                } else {
                    console.error(`❌ Failed to send FCM notification: ${result.error}`);
                    
                    // If token is invalid, we might want to handle it (e.g., mark user as inactive)
                    if (result.invalidToken) {
                        console.log(`Invalid FCM token for user ${stock.user_id}`);
                    }
                }
            } else {
                console.log(`No FCM token for stock ${stock.symbol}`);
            }

            // Mark as triggered
            await Stock.markAsTriggered(stock.id);
            console.log(`Stock ${stock.symbol} marked as triggered`);
        } else {
            console.log(`${stock.symbol}: No trigger (target: ${stock.target_return_rate}%, current: ${returnRate.toFixed(2)}%)`);
        }

        return { success: true, triggered: shouldTrigger, price: currentPrice };
    } catch (error) {
        console.error(`Error checking ${stock.symbol}:`, error.message);
        return { success: false, error: error.message };
    }
}

async function runPriceCheck() {
    console.log('\n========================================');
    console.log(`🔍 Starting price check at ${new Date().toISOString()}`);
    console.log('========================================\n');

    try {
        // Get all active stocks with user FCM tokens
        const activeStocks = await Stock.findAllActive();
        
        if (activeStocks.length === 0) {
            console.log('No active stocks to monitor');
            return;
        }

        console.log(`Found ${activeStocks.length} active stocks to monitor\n`);

        // Group stocks by symbol to avoid duplicate API calls
        const stocksBySymbol = {};
        activeStocks.forEach(stock => {
            if (!stocksBySymbol[stock.symbol]) {
                stocksBySymbol[stock.symbol] = [];
            }
            stocksBySymbol[stock.symbol].push(stock);
        });

        // Check each unique symbol
        for (const symbol of Object.keys(stocksBySymbol)) {
            const stocks = stocksBySymbol[symbol];
            
            try {
                // Get price once for all stocks with same symbol
                const priceData = await AlphaVantageService.getCurrentPrice(symbol);
                const currentPrice = priceData.price;
                
                console.log(`\n📊 ${symbol}: Current price = $${currentPrice}`);

                // Check each stock with this symbol
                for (const stock of stocks) {
                    const returnRate = stock.buy_price > 0 
                        ? ((currentPrice - stock.buy_price) / stock.buy_price) * 100 
                        : 0;
                    
                    const shouldTrigger = returnRate >= stock.target_return_rate || currentPrice >= stock.target_price;

                    if (shouldTrigger) {
                        console.log(`  🚨 Stock ${stock.id} triggered! Return: ${returnRate.toFixed(2)}%`);
                        
                        // Send notification
                        if (stock.fcm_token) {
                            const result = await FcmService.sendPriceAlert(stock.fcm_token, stock, currentPrice);
                            if (result.success) {
                                console.log(`  ✅ Notification sent`);
                            } else {
                                console.error(`  ❌ Notification failed: ${result.error}`);
                            }
                        }

                        // Mark as triggered
                        await Stock.markAsTriggered(stock.id);
                    } else {
                        console.log(`  ✓ Stock ${stock.id}: No trigger (return: ${returnRate.toFixed(2)}%)`);
                    }
                }
            } catch (error) {
                console.error(`  ❌ Error checking ${symbol}:`, error.message);
            }
        }

    } catch (error) {
        console.error('Error in price check job:', error);
    }

    console.log('\n========================================');
    console.log(`✅ Price check completed at ${new Date().toISOString()}`);
    console.log('========================================\n');
}

// Schedule the job to run every minute
// Cron format: second(optional) minute hour day month day-of-week
const scheduledJob = cron.schedule('* * * * *', runPriceCheck, {
    scheduled: false, // Don't start automatically
    timezone: 'Asia/Shanghai' // Use your timezone
});

module.exports = {
    scheduledJob,
    runPriceCheck,
    checkStockPrice
};
