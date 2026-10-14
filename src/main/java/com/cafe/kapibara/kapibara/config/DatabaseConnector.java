package com.cafe.kapibara.kapibara.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

// SQL
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

// HIKARI
    public final class DatabaseConnector {

    private static final HikariDataSource DATA_SOURCE;

    static {
        Properties properties = new Properties();

        try (InputStream input = DatabaseConnector.class.getResourceAsStream("/db.properties")) {
            if (input == null) {
                System.err.println("DatabaseConnector: db.properties not found! Falling back to defaults.");
            } else {
                properties.load(input);
            }
        } catch (IOException e) {
            System.err.println("DatabaseConnector: Error reading db.properties: " + e.getMessage());
        }

        String host = properties.getProperty("db.host", "localhost");
        String port = properties.getProperty("db.port", "3306");
        String dbName = properties.getProperty("db.name", "coffee_pos");
        String user = properties.getProperty("db.user", "root");
        String password = properties.getProperty("db.password", "");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + dbName);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("org.mariadb.jdbc.Driver");

        //CONNECTION POOL SIZING AND TIMEOUTS
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);

        try {
            DATA_SOURCE = new HikariDataSource(config);
        } catch (Exception e) {
            System.err.println("DatabaseConfig: Critical failure initializing pool: " + e.getMessage());
            throw new ExceptionInInitializerError(e);
        }
    }

    private DatabaseConnector() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static Connection getConnection() throws SQLException {
        if (DATA_SOURCE == null || DATA_SOURCE.isClosed()) {
            throw new SQLException("Connection pool is not available.");
        }
        return DATA_SOURCE.getConnection();
    }

    public static void closePool() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }

    //PANG TEST NG CONNECTION
    public static void main(String[] args) {
        System.out.println("Testing MariaDB connection via HikariCP...");
        try (Connection conn = getConnection()) {
            System.out.println("Connection status: SUCCESS!");
            System.out.println("Connected to: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            System.err.println("Connection status: FAILED!");
            e.printStackTrace();
        } finally {
            closePool();
        }
    }
}