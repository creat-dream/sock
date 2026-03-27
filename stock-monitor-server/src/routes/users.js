const express = require('express');
const router = express.Router();
const { v4: uuidv4 } = require('uuid');
const User = require('../models/User');

// Register or update user with FCM token
router.post('/register', async (req, res) => {
    try {
        const { userId, fcmToken } = req.body;

        if (!userId || !fcmToken) {
            return res.status(400).json({
                success: false,
                message: 'userId and fcmToken are required'
            });
        }

        await User.create({
            id: userId,
            fcmToken: fcmToken
        });

        res.json({
            success: true,
            message: 'User registered successfully',
            data: { userId }
        });
    } catch (error) {
        console.error('Error registering user:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to register user',
            error: error.message
        });
    }
});

// Update FCM token
router.put('/:userId/token', async (req, res) => {
    try {
        const { userId } = req.params;
        const { fcmToken } = req.body;

        if (!fcmToken) {
            return res.status(400).json({
                success: false,
                message: 'fcmToken is required'
            });
        }

        const result = await User.updateFcmToken(userId, fcmToken);

        if (result.changes === 0) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        res.json({
            success: true,
            message: 'FCM token updated successfully'
        });
    } catch (error) {
        console.error('Error updating FCM token:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to update FCM token',
            error: error.message
        });
    }
});

// Get user by ID
router.get('/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const user = await User.findById(userId);

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        res.json({
            success: true,
            data: user
        });
    } catch (error) {
        console.error('Error getting user:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to get user',
            error: error.message
        });
    }
});

// Delete user
router.delete('/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const result = await User.delete(userId);

        if (result.changes === 0) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        res.json({
            success: true,
            message: 'User deleted successfully'
        });
    } catch (error) {
        console.error('Error deleting user:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to delete user',
            error: error.message
        });
    }
});

module.exports = router;
