const admin = require('firebase-admin');
const path = require('path');

let isInitialized = false;

function initializeFirebase() {
    if (isInitialized) return;

    try {
        const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './config/serviceAccountKey.json';
        const serviceAccount = require(path.resolve(serviceAccountPath));

        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });

        isInitialized = true;
        console.log('Firebase Admin SDK initialized successfully');
    } catch (error) {
        console.error('Failed to initialize Firebase Admin SDK:', error.message);
        console.log('FCM notifications will be disabled');
    }
}

class FcmService {
    static async sendPriceAlert(fcmToken, stockData, currentPrice) {
        if (!isInitialized) {
            initializeFirebase();
        }

        if (!isInitialized) {
            console.log('FCM not initialized, skipping notification');
            return { success: false, error: 'FCM not initialized' };
        }

        const { symbol, buy_price, target_return_rate, target_price } = stockData;
        
        // Calculate return rate
        const returnRate = buy_price > 0 
            ? ((currentPrice - buy_price) / buy_price) * 100 
            : 0;

        const message = {
            notification: {
                title: `股票提醒: ${symbol}`,
                body: `当前价格 $${currentPrice.toFixed(2)}，收益率 ${returnRate.toFixed(2)}%`
            },
            data: {
                stockId: stockData.id,
                symbol: symbol,
                currentPrice: currentPrice.toString(),
                buyPrice: buy_price.toString(),
                returnRate: returnRate.toString(),
                type: 'PRICE_ALERT',
                timestamp: Date.now().toString()
            },
            token: fcmToken,
            android: {
                priority: 'high',
                notification: {
                    channelId: 'stock_alerts',
                    sound: 'default',
                    priority: 'high'
                }
            }
        };

        try {
            const response = await admin.messaging().send(message);
            console.log(`FCM notification sent successfully: ${response}`);
            return { success: true, messageId: response };
        } catch (error) {
            console.error('Error sending FCM notification:', error);
            
            // Handle invalid token
            if (error.code === 'messaging/registration-token-not-registered') {
                return { success: false, error: 'Invalid FCM token', invalidToken: true };
            }
            
            return { success: false, error: error.message };
        }
    }

    static async sendMulticastNotification(fcmTokens, notificationData) {
        if (!isInitialized) {
            initializeFirebase();
        }

        if (!isInitialized) {
            console.log('FCM not initialized, skipping multicast notification');
            return { success: false, error: 'FCM not initialized' };
        }

        const { title, body, data } = notificationData;

        const message = {
            notification: { title, body },
            data: data || {},
            tokens: fcmTokens,
            android: {
                priority: 'high',
                notification: {
                    channelId: 'stock_alerts',
                    sound: 'default'
                }
            }
        };

        try {
            const response = await admin.messaging().sendEachForMulticast(message);
            console.log(`Multicast sent: ${response.successCount} successful, ${response.failureCount} failed`);
            return {
                success: true,
                successCount: response.successCount,
                failureCount: response.failureCount,
                responses: response.responses
            };
        } catch (error) {
            console.error('Error sending multicast notification:', error);
            return { success: false, error: error.message };
        }
    }
}

module.exports = FcmService;
