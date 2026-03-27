const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');

// Ensure data directory exists
const dataDir = path.dirname(process.env.DB_PATH || './data/stocks.db');
if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
}

const dbPath = process.env.DB_PATH || './data/stocks.db';

const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('Error opening database:', err.message);
    } else {
        console.log('Connected to SQLite database.');
        initializeTables();
    }
});

function initializeTables() {
    // Users table
    db.run(`
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            fcm_token TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            last_active DATETIME
        )
    `, (err) => {
        if (err) console.error('Error creating users table:', err.message);
    });

    // Stocks table
    db.run(`
        CREATE TABLE IF NOT EXISTS stocks (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            symbol TEXT NOT NULL,
            buy_price REAL NOT NULL,
            quantity REAL NOT NULL,
            target_return_rate REAL NOT NULL,
            target_price REAL NOT NULL,
            is_triggered INTEGER DEFAULT 0,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            triggered_at DATETIME,
            FOREIGN KEY (user_id) REFERENCES users(id)
        )
    `, (err) => {
        if (err) console.error('Error creating stocks table:', err.message);
    });

    // Create indexes
    db.run(`CREATE INDEX IF NOT EXISTS idx_stocks_user_id ON stocks(user_id)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_stocks_symbol ON stocks(symbol)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_stocks_triggered ON stocks(is_triggered)`);
}

// Promisify database methods
const dbAsync = {
    run: (sql, params = []) => {
        return new Promise((resolve, reject) => {
            db.run(sql, params, function(err) {
                if (err) reject(err);
                else resolve({ id: this.lastID, changes: this.changes });
            });
        });
    },
    get: (sql, params = []) => {
        return new Promise((resolve, reject) => {
            db.get(sql, params, (err, row) => {
                if (err) reject(err);
                else resolve(row);
            });
        });
    },
    all: (sql, params = []) => {
        return new Promise((resolve, reject) => {
            db.all(sql, params, (err, rows) => {
                if (err) reject(err);
                else resolve(rows);
            });
        });
    }
};

module.exports = { db, dbAsync };
