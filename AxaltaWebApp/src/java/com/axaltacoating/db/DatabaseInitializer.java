package com.axaltacoating.db;

import com.axaltacoating.util.DatabaseConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class DatabaseInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            initializeDatabase(sce);
        } catch (Exception e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeDatabase(ServletContextEvent sce) throws ClassNotFoundException, SQLException, IOException {
        String dbHost = DatabaseConnection.getDbHost();
        String dbPort = DatabaseConnection.getDbPort();
        String dbName = DatabaseConnection.getDbName();
        String dbUser = DatabaseConnection.getDbUser();
        String dbPassword = DatabaseConnection.getDbPassword();
        String serverUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=UTF-8&serverTimezone=UTC";

        try {
            // Load MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Connect to server and create database if not exists, with retry logic for container startup
            int maxRetries = 15;
            int retryCount = 0;
            boolean connected = false;

            while (retryCount < maxRetries && !connected) {
                try (Connection conn = DriverManager.getConnection(serverUrl, dbUser, dbPassword);
                     Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);
                    connected = true;
                    System.out.println("Connected to MySQL server and ensured database '" + dbName + "' exists.");
                } catch (SQLException ex) {
                    retryCount++;
                    System.out.println("Waiting for MySQL server at " + dbHost + ":" + dbPort + " (attempt " + retryCount + " of " + maxRetries + ")...");
                    if (retryCount >= maxRetries) {
                        System.err.println("Could not connect to MySQL server after " + maxRetries + " attempts: " + ex.getMessage());
                        throw ex;
                    }
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // Now connect to the database and initialize tables
            DatabaseConnection.initializeTables();

            // Run additional SQL scripts if present in WEB-INF
            try (Connection conn = DatabaseConnection.getConnection()) {
                executeSqlScript(sce, conn, "/WEB-INF/sql/init_database.sql");
                executeSqlScript(sce, conn, "/WEB-INF/sql/migrate_kanban_fields.sql");
                executeSqlScript(sce, conn, "/WEB-INF/sql/migrate_inventory_table.sql");
                System.out.println("Supplexio database initialized and migrated successfully!");
            }

        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    private void executeSqlScript(ServletContextEvent sce, Connection conn, String scriptPath) {
        ServletContext context = sce.getServletContext();
        try (InputStream inputStream = context.getResourceAsStream(scriptPath)) {
            if (inputStream != null) {
                String sql;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    sql = reader.lines().collect(Collectors.joining("\n"));
                }

                String[] statements = sql.split(";");
                try (Statement stmt = conn.createStatement()) {
                    for (String statement : statements) {
                        String trimmed = statement.trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                stmt.execute(trimmed);
                            } catch (SQLException se) {
                                // Ignore benign errors like column already exists or table exists
                                System.out.println("Notice executing script statement: " + se.getMessage());
                            }
                        }
                    }
                }
                System.out.println("Executed SQL script: " + scriptPath);
            } else {
                System.out.println("Optional SQL script not found: " + scriptPath);
            }
        } catch (Exception e) {
            System.err.println("Error executing script " + scriptPath + ": " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Cleanup if needed
    }
}
