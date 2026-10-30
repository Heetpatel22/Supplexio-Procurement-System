package com.axaltacoating.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseConnection {
    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());
    private static final CountDownLatch initLatch = new CountDownLatch(1);
    private static volatile boolean tablesInitialized = false;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "MySQL JDBC Driver not found", e);
        }
    }

    private static String getEnv(String name, String defaultValue) {
        String val = System.getenv(name);
        if (val == null || val.trim().isEmpty()) {
            val = System.getProperty(name.toLowerCase().replace('_', '.'));
        }
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
    }

    public static String getDbHost() {
        return getEnv("DB_HOST", "mysql");
    }

    public static String getDbPort() {
        return getEnv("DB_PORT", "3306");
    }

    public static String getDbName() {
        return getEnv("DB_NAME", "axalta");
    }

    public static String getDbUser() {
        return getEnv("DB_USER", "root");
    }

    public static String getDbPassword() {
        return getEnv("DB_PASSWORD", "root");
    }

    public static String getDbUrl() {
        String customUrl = getEnv("DB_URL", null);
        if (customUrl != null) {
            return customUrl;
        }
        return "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=UTF-8&serverTimezone=UTC";
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getDbUrl(), getDbUser(), getDbPassword());
    }

    public static void initializeTables() throws SQLException {
        if (tablesInitialized) {
            return;
        }

        Connection conn = null;
        Statement stmt = null;

        try {
            conn = getConnection();
            stmt = conn.createStatement();

            // 1. Create users table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "username VARCHAR(255) NOT NULL UNIQUE, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "role VARCHAR(50) NOT NULL DEFAULT 'USER', " +
                "status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', " +
                "email VARCHAR(255) NOT NULL UNIQUE, " +
                "last_login TIMESTAMP NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")"
            );

            // Insert default admin user if not exists
            stmt.executeUpdate(
                "INSERT IGNORE INTO users (id, username, password_hash, role, status, email) VALUES " +
                "(1, 'admin', 'admin123', 'ADMIN', 'ACTIVE', 'admin@example.com')"
            );

            // 2. Create inventory table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS inventory (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "item_name VARCHAR(255) NOT NULL, " +
                "quantity INT NOT NULL, " +
                "min_threshold INT NOT NULL, " +
                "max_threshold INT NOT NULL, " +
                "kanban_status ENUM('Low', 'Medium', 'High') DEFAULT 'Medium', " +
                "needs_auction BOOLEAN DEFAULT FALSE, " +
                "auction_started BOOLEAN DEFAULT FALSE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")"
            );

            // 3. Create products table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS products (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(255) NOT NULL, " +
                "description TEXT, " +
                "category VARCHAR(100) DEFAULT 'General', " +
                "base_price DECIMAL(10,2) DEFAULT 0.00, " +
                "unit VARCHAR(50) DEFAULT 'pcs', " +
                "stock_quantity INT DEFAULT 0, " +
                "inventory_id BIGINT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // 4. Create suppliers table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS suppliers (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(255) NOT NULL, " +
                "email VARCHAR(255) NOT NULL UNIQUE, " +
                "phone VARCHAR(20), " +
                "address TEXT, " +
                "city VARCHAR(100), " +
                "state VARCHAR(100), " +
                "country VARCHAR(100), " +
                "postal_code VARCHAR(20), " +
                "company_name VARCHAR(255) NULL, " +
                "contact_person VARCHAR(255) NULL, " +
                "user_id INT NULL, " +
                "password VARCHAR(255) NOT NULL DEFAULT 'password123', " +
                "status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE', " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL" +
                ")"
            );

            // 5. Create reverse auctions table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS reverse_auctions (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "product_id BIGINT NOT NULL, " +
                "start_price DOUBLE NOT NULL, " +
                "current_price DOUBLE NOT NULL, " +
                "required_quantity INT DEFAULT 1, " +
                "winning_supplier_id BIGINT NULL, " +
                "start_time TIMESTAMP NOT NULL, " +
                "end_time TIMESTAMP NOT NULL, " +
                "status ENUM('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'SCHEDULED') DEFAULT 'PENDING', " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (product_id) REFERENCES products(id), " +
                "FOREIGN KEY (winning_supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL" +
                ")"
            );

            // 6. Create bids table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS bids (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "auction_id BIGINT NOT NULL, " +
                "supplier_id BIGINT NULL, " +
                "user_id BIGINT NULL, " +
                "amount DOUBLE NOT NULL DEFAULT 0, " +
                "bid_amount DOUBLE NOT NULL DEFAULT 0, " +
                "bid_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (auction_id) REFERENCES reverse_auctions(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE" +
                ")"
            );

            // 7. Create auction invitations table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS auction_invitations (" +
                "auction_id BIGINT NOT NULL, " +
                "supplier_id BIGINT NOT NULL, " +
                "status ENUM('PENDING', 'ACCEPTED', 'DECLINED') DEFAULT 'PENDING', " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (auction_id, supplier_id), " +
                "FOREIGN KEY (auction_id) REFERENCES reverse_auctions(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE" +
                ")"
            );

            // 8. Create payments table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS payments (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "auction_id BIGINT NOT NULL, " +
                "supplier_id BIGINT NOT NULL, " +
                "amount DOUBLE NOT NULL, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                "payment_method VARCHAR(20), " +
                "transaction_id VARCHAR(100), " +
                "payment_date TIMESTAMP NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "remarks TEXT, " +
                "FOREIGN KEY (auction_id) REFERENCES reverse_auctions(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE" +
                ")"
            );

            // 9. Create purchase orders table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS purchase_orders (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "po_number INT NULL, " +
                "auction_id BIGINT NOT NULL, " +
                "supplier_id BIGINT NOT NULL, " +
                "company_name VARCHAR(255), " +
                "material VARCHAR(255), " +
                "amount DOUBLE NOT NULL, " +
                "status ENUM('PENDING', 'SENT', 'DELIVERED') DEFAULT 'PENDING', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "buyer_id BIGINT, " +
                "delivery_address VARCHAR(255), " +
                "payment_terms VARCHAR(255), " +
                "description TEXT, " +
                "expected_delivery_date DATE, " +
                "incoterm VARCHAR(10), " +
                "incoterm_location VARCHAR(255), " +
                "FOREIGN KEY (auction_id) REFERENCES reverse_auctions(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE" +
                ")"
            );

            // 10. Create auction deliveries table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS auction_deliveries (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "auction_id BIGINT NOT NULL, " +
                "supplier_id BIGINT NOT NULL, " +
                "winning_amount DOUBLE NOT NULL, " +
                "delivered_quantity INT DEFAULT 0, " +
                "status ENUM('PENDING', 'DELIVERED', 'CANCELLED') DEFAULT 'PENDING', " +
                "delivery_date TIMESTAMP NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (auction_id) REFERENCES reverse_auctions(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE" +
                ")"
            );

            // 11. Create auction inventory sync table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS auction_inventory_sync (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "inventory_id INT NOT NULL, " +
                "product_id BIGINT NULL, " +
                "auction_id BIGINT NULL, " +
                "sync_status ENUM('PENDING', 'AUCTION_CREATED', 'COMPLETED') DEFAULT 'PENDING', " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL, " +
                "FOREIGN KEY (auction_id) REFERENCES reverse_auctions(id) ON DELETE SET NULL" +
                ")"
            );

            // 12. Create bid chart data table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS bid_chart_data (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "auction_id BIGINT NOT NULL, " +
                "bid_time TIMESTAMP NOT NULL, " +
                "bid_amount DOUBLE NOT NULL, " +
                "supplier_id BIGINT NOT NULL, " +
                "supplier_name VARCHAR(255), " +
                "bid_id BIGINT, " +
                "INDEX idx_auction_time (auction_id, bid_time)" +
                ")"
            );

            // 13. Create todos table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS todos (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "title VARCHAR(255) NOT NULL, " +
                "description TEXT, " +
                "due_date DATE, " +
                "priority VARCHAR(50) DEFAULT 'Medium', " +
                "status VARCHAR(50) DEFAULT 'Pending', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")"
            );

            // Insert initial inventory items if table is empty
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM inventory");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate(
                    "INSERT INTO inventory (id, item_name, quantity, min_threshold, max_threshold, kanban_status, needs_auction, auction_started) VALUES " +
                    "(1, 'Steel Rods', 50, 20, 100, 'Medium', FALSE, FALSE), " +
                    "(2, 'Copper Sheets', 30, 10, 80, 'High', FALSE, FALSE), " +
                    "(3, 'Aluminum Plates', 80, 40, 120, 'Medium', FALSE, FALSE), " +
                    "(4, 'Plastic Granules', 120, 60, 200, 'Medium', FALSE, FALSE), " +
                    "(5, 'Paint Buckets', 15, 25, 100, 'Low', TRUE, FALSE)"
                );
            }
            rs.close();

            // Insert sample products for inventory if table is empty
            rs = stmt.executeQuery("SELECT COUNT(*) FROM products");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate(
                    "INSERT INTO products (name, description, category, base_price, unit, stock_quantity, inventory_id) VALUES " +
                    "('Steel Rods', 'Industrial grade steel rods', 'Raw Materials', 150.00, 'pcs', 50, 1), " +
                    "('Copper Sheets', 'High conductivity copper sheets', 'Raw Materials', 220.00, 'pcs', 30, 2), " +
                    "('Aluminum Plates', 'Aerospace alloy aluminum plates', 'Raw Materials', 180.00, 'pcs', 80, 3), " +
                    "('Plastic Granules', 'Polypropylene plastic granules', 'Polymers', 45.00, 'kg', 120, 4), " +
                    "('Industrial Primer - White', 'Anti-corrosion white primer', 'Coatings', 65.00, 'gallon', 15, 5)"
                );
            }
            rs.close();

            // Insert sample suppliers if table is empty
            rs = stmt.executeQuery("SELECT COUNT(*) FROM suppliers");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate(
                    "INSERT INTO suppliers (name, email, phone, address, company_name, contact_person, password, status) VALUES " +
                    "('Acme Industrial Supplies', 'supplier1@example.com', '+1-555-0101', '100 Supply Ave', 'Acme Corp', 'John Doe', 'password123', 'ACTIVE'), " +
                    "('Apex Metals & Coatings', 'supplier2@example.com', '+1-555-0102', '200 Forge Rd', 'Apex Ltd', 'Jane Smith', 'password123', 'ACTIVE'), " +
                    "('Global Chemical Solutions', 'supplier3@example.com', '+1-555-0103', '300 Polymer Way', 'Global Chem', 'Bob Wilson', 'password123', 'ACTIVE')"
                );
            }
            rs.close();

            tablesInitialized = true;
            LOGGER.info("All Supplexio database tables and seed data initialized successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing database tables", e);
            throw e;
        } finally {
            closeQuietly(null, stmt, conn);
            initLatch.countDown();
        }
    }

    public static boolean waitForInitialization(long timeout, TimeUnit unit) throws InterruptedException {
        return initLatch.await(timeout, unit);
    }

    public static boolean isInitialized() {
        return tablesInitialized;
    }

    public static void closeQuietly(ResultSet rs, Statement stmt, Connection conn) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing ResultSet", e);
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing Statement", e);
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing Connection", e);
            }
        }
    }
}
