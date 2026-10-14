package com.cafe.kapibara.kapibara.config;

import com.cafe.kapibara.kapibara.util.PasswordHasher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseSeeder {

    public static void seedAdmin() {
        String sql = "INSERT INTO users (username, password, role, full_name, is_active) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "admin");
            String hashedPassword = PasswordHasher.hash("admin123");
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, "ADMIN");
            pstmt.setString(4, "System Administrator");
            pstmt.setInt(5, 1);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("Admin user seeded successfully with a hashed password.");
            }

        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                System.out.println("Admin user already exists. Seeding skipped.");
            } else {
                System.err.println("Error seeding database: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting database seeder...");
        seedAdmin();
        DatabaseConnector.closePool();
        System.out.println("Seeding process completed.");
    }
}