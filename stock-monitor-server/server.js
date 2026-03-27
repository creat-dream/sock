const app = require('./src/app');

const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
    console.log(`
╔════════════════════════════════════════════════════════╗
║                                                        ║
║         📈 Stock Monitor Server v1.0.0                 ║
║                                                        ║
╠════════════════════════════════════════════════════════╣
║  Server running on port: ${PORT}                        ║
║  Environment: ${process.env.NODE_ENV || 'development'}                    ║
║                                                        ║
║  API Endpoints:                                        ║
║    • POST   /api/users/register                        ║
║    • GET    /api/users/:userId                         ║
║    • PUT    /api/users/:userId/token                   ║
║    • DELETE /api/users/:userId                         ║
║                                                        ║
║    • POST   /api/stocks                                ║
║    • GET    /api/stocks/user/:userId                   ║
║    • GET    /api/stocks/user/:userId/active            ║
║    • GET    /api/stocks/:stockId                       ║
║    • PUT    /api/stocks/:stockId                       ║
║    • DELETE /api/stocks/:stockId                       ║
║                                                        ║
║    • GET    /health                                    ║
║                                                        ║
╚════════════════════════════════════════════════════════╝
    `);
});
