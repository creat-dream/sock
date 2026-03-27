const express = require('express');
const router = express.Router();
const { v4: uuidv4 } = require('uuid');
const Stock = require('../models/Stock');

// Add new stock
router.post('/', async (req, res) => {
    try {
        const { userId, symbol, buyPrice, quantity, targetReturnRate, targetPrice } = req.body;

        // Validation
        if (!userId || !symbol || buyPrice === undefined || quantity === undefined || 
            targetReturnRate === undefined || targetPrice === undefined) {
            return res.status(400).json({
                success: false,
                message: 'All fields are required: userId, symbol, buyPrice, quantity, targetReturnRate, targetPrice'
            });
        }

        const stockId = uuidv4();
        await Stock.create({
            id: stockId,
            userId,
            symbol: symbol.toUpperCase(),
            buyPrice,
            quantity,
            targetReturnRate,
            targetPrice
        });

        res.status(201).json({
            success: true,
            message: 'Stock added successfully',
            data: { id: stockId }
        });
    } catch (error) {
        console.error('Error adding stock:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to add stock',
            error: error.message
        });
    }
});

// Get all stocks for a user
router.get('/user/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const stocks = await Stock.findByUserId(userId);

        res.json({
            success: true,
            data: stocks
        });
    } catch (error) {
        console.error('Error getting stocks:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to get stocks',
            error: error.message
        });
    }
});

// Get active stocks for a user
router.get('/user/:userId/active', async (req, res) => {
    try {
        const { userId } = req.params;
        const stocks = await Stock.findActiveByUserId(userId);

        res.json({
            success: true,
            data: stocks
        });
    } catch (error) {
        console.error('Error getting active stocks:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to get active stocks',
            error: error.message
        });
    }
});

// Get single stock
router.get('/:stockId', async (req, res) => {
    try {
        const { stockId } = req.params;
        const stock = await Stock.findById(stockId);

        if (!stock) {
            return res.status(404).json({
                success: false,
                message: 'Stock not found'
            });
        }

        res.json({
            success: true,
            data: stock
        });
    } catch (error) {
        console.error('Error getting stock:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to get stock',
            error: error.message
        });
    }
});

// Update stock
router.put('/:stockId', async (req, res) => {
    try {
        const { stockId } = req.params;
        const { buyPrice, quantity, targetReturnRate, targetPrice } = req.body;

        const result = await Stock.update(stockId, {
            buyPrice,
            quantity,
            targetReturnRate,
            targetPrice
        });

        if (result.changes === 0) {
            return res.status(404).json({
                success: false,
                message: 'Stock not found'
            });
        }

        res.json({
            success: true,
            message: 'Stock updated successfully'
        });
    } catch (error) {
        console.error('Error updating stock:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to update stock',
            error: error.message
        });
    }
});

// Delete stock
router.delete('/:stockId', async (req, res) => {
    try {
        const { stockId } = req.params;
        const result = await Stock.delete(stockId);

        if (result.changes === 0) {
            return res.status(404).json({
                success: false,
                message: 'Stock not found'
            });
        }

        res.json({
            success: true,
            message: 'Stock deleted successfully'
        });
    } catch (error) {
        console.error('Error deleting stock:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to delete stock',
            error: error.message
        });
    }
});

module.exports = router;
