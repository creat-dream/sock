const axios = require('axios');

const BASE_URL = 'https://www.alphavantage.co/query';
const API_KEY = process.env.ALPHA_VANTAGE_API_KEY;

// Rate limiting: 5 calls per minute, 500 per day for free tier
const requestQueue = [];
let lastRequestTime = 0;
const MIN_INTERVAL = 12000; // 12 seconds between requests (5 per minute)

async function makeRequest(params) {
    const now = Date.now();
    const timeSinceLastRequest = now - lastRequestTime;
    
    if (timeSinceLastRequest < MIN_INTERVAL) {
        await new Promise(resolve => setTimeout(resolve, MIN_INTERVAL - timeSinceLastRequest));
    }

    try {
        lastRequestTime = Date.now();
        const response = await axios.get(BASE_URL, {
            params: {
                ...params,
                apikey: API_KEY
            },
            timeout: 30000
        });

        // Check for API error messages
        if (response.data.Note) {
            throw new Error(`API Rate Limit: ${response.data.Note}`);
        }
        if (response.data.Information) {
            throw new Error(`API Error: ${response.data.Information}`);
        }

        return response.data;
    } catch (error) {
        if (error.response) {
            throw new Error(`HTTP ${error.response.status}: ${error.response.statusText}`);
        }
        throw error;
    }
}

class AlphaVantageService {
    /**
     * Get current price for a stock symbol
     * @param {string} symbol - Stock symbol (e.g., "AAPL", "600036.SH")
     * @returns {Promise<{symbol: string, price: number, change: number, changePercent: number}>}
     */
    static async getCurrentPrice(symbol) {
        const data = await makeRequest({
            function: 'GLOBAL_QUOTE',
            symbol: symbol
        });

        const quote = data['Global Quote'];
        if (!quote || !quote['01. symbol']) {
            throw new Error('Invalid response from Alpha Vantage API');
        }

        return {
            symbol: quote['01. symbol'],
            price: parseFloat(quote['05. price']) || 0,
            change: parseFloat(quote['09. change']) || 0,
            changePercent: parseFloat(quote['10. change percent']?.replace('%', '')) || 0
        };
    }

    /**
     * Get daily time series data for historical prices
     * @param {string} symbol - Stock symbol
     * @param {string} outputSize - "compact" (100 data points) or "full" (20+ years)
     * @returns {Promise<Array<{date: string, open: number, high: number, low: number, close: number, volume: number}>>}
     */
    static async getTimeSeriesDaily(symbol, outputSize = 'full') {
        const data = await makeRequest({
            function: 'TIME_SERIES_DAILY',
            symbol: symbol,
            outputsize: outputSize
        });

        const timeSeries = data['Time Series (Daily)'];
        if (!timeSeries) {
            throw new Error('No time series data available');
        }

        return Object.entries(timeSeries).map(([date, values]) => ({
            date,
            open: parseFloat(values['1. open']) || 0,
            high: parseFloat(values['2. high']) || 0,
            low: parseFloat(values['3. low']) || 0,
            close: parseFloat(values['4. close']) || 0,
            volume: parseInt(values['5. volume']) || 0
        }));
    }

    /**
     * Get historical prices for the past year
     * @param {string} symbol - Stock symbol
     * @returns {Promise<Array>}
     */
    static async getYearlyHistory(symbol) {
        const allData = await this.getTimeSeriesDaily(symbol, 'full');
        
        // Filter to last 365 days
        const oneYearAgo = new Date();
        oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
        
        return allData.filter(item => new Date(item.date) >= oneYearAgo);
    }
}

module.exports = AlphaVantageService;
