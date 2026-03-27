const { dbAsync } = require('../config/database');

class Stock {
    static async create(stockData) {
        const { id, userId, symbol, buyPrice, quantity, targetReturnRate, targetPrice } = stockData;
        const sql = `
            INSERT INTO stocks (id, user_id, symbol, buy_price, quantity, target_return_rate, target_price)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `;
        return dbAsync.run(sql, [id, userId, symbol, buyPrice, quantity, targetReturnRate, targetPrice]);
    }

    static async findById(id) {
        const sql = `
            SELECT s.*, u.fcm_token 
            FROM stocks s
            JOIN users u ON s.user_id = u.id
            WHERE s.id = ?
        `;
        return dbAsync.get(sql, [id]);
    }

    static async findByUserId(userId) {
        const sql = `
            SELECT s.*, u.fcm_token 
            FROM stocks s
            JOIN users u ON s.user_id = u.id
            WHERE s.user_id = ?
            ORDER BY s.created_at DESC
        `;
        return dbAsync.all(sql, [userId]);
    }

    static async findActiveByUserId(userId) {
        const sql = `
            SELECT s.*, u.fcm_token 
            FROM stocks s
            JOIN users u ON s.user_id = u.id
            WHERE s.user_id = ? AND s.is_triggered = 0
            ORDER BY s.created_at DESC
        `;
        return dbAsync.all(sql, [userId]);
    }

    static async findAllActive() {
        const sql = `
            SELECT s.*, u.fcm_token 
            FROM stocks s
            JOIN users u ON s.user_id = u.id
            WHERE s.is_triggered = 0
        `;
        return dbAsync.all(sql);
    }

    static async update(id, updates) {
        const fields = [];
        const values = [];

        if (updates.buyPrice !== undefined) {
            fields.push('buy_price = ?');
            values.push(updates.buyPrice);
        }
        if (updates.quantity !== undefined) {
            fields.push('quantity = ?');
            values.push(updates.quantity);
        }
        if (updates.targetReturnRate !== undefined) {
            fields.push('target_return_rate = ?');
            values.push(updates.targetReturnRate);
        }
        if (updates.targetPrice !== undefined) {
            fields.push('target_price = ?');
            values.push(updates.targetPrice);
        }

        if (fields.length === 0) return { changes: 0 };

        const sql = `UPDATE stocks SET ${fields.join(', ')} WHERE id = ?`;
        values.push(id);
        return dbAsync.run(sql, values);
    }

    static async markAsTriggered(id) {
        const sql = `
            UPDATE stocks 
            SET is_triggered = 1, triggered_at = datetime('now')
            WHERE id = ?
        `;
        return dbAsync.run(sql, [id]);
    }

    static async delete(id) {
        const sql = 'DELETE FROM stocks WHERE id = ?';
        return dbAsync.run(sql, [id]);
    }

    static async deleteByUserId(userId) {
        const sql = 'DELETE FROM stocks WHERE user_id = ?';
        return dbAsync.run(sql, [userId]);
    }

    // Helper method to check if stock should trigger
    shouldTrigger(currentPrice) {
        if (this.is_triggered) return false;
        
        const currentReturn = this.buy_price > 0 
            ? ((currentPrice - this.buy_price) / this.buy_price) * 100 
            : 0;
        
        return currentReturn >= this.target_return_rate || currentPrice >= this.target_price;
    }
}

module.exports = Stock;
