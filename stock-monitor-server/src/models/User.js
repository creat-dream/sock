const { dbAsync } = require('../config/database');

class User {
    static async create(userData) {
        const { id, fcmToken } = userData;
        const sql = `
            INSERT INTO users (id, fcm_token, last_active)
            VALUES (?, ?, datetime('now'))
            ON CONFLICT(id) DO UPDATE SET
                fcm_token = excluded.fcm_token,
                last_active = datetime('now')
        `;
        return dbAsync.run(sql, [id, fcmToken]);
    }

    static async findById(id) {
        const sql = 'SELECT * FROM users WHERE id = ?';
        return dbAsync.get(sql, [id]);
    }

    static async updateFcmToken(id, fcmToken) {
        const sql = `
            UPDATE users 
            SET fcm_token = ?, last_active = datetime('now')
            WHERE id = ?
        `;
        return dbAsync.run(sql, [fcmToken, id]);
    }

    static async delete(id) {
        const sql = 'DELETE FROM users WHERE id = ?';
        return dbAsync.run(sql, [id]);
    }

    static async getAll() {
        const sql = 'SELECT * FROM users ORDER BY created_at DESC';
        return dbAsync.all(sql);
    }
}

module.exports = User;
