package com.portal;

import java.sql.*;

public class DatabaseInitializer {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:hospital.db";  // will be created in project root

        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Connected to SQLite database");

            // Create tables
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Account (
                        user_name TEXT PRIMARY KEY,
                        password TEXT NOT NULL,
                        email TEXT
                    )
                """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS PatientAccount (
                        user_name TEXT PRIMARY KEY,
                        age INTEGER,
                        ssn TEXT,
                        FOREIGN KEY(user_name) REFERENCES Account(user_name)
                    )
                """);
            }

            // Insert John Marshall
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Account (user_name, password, email) VALUES (?, ?, ?)")) {
                pstmt.setString(1, "johnm");
                pstmt.setString(2, "secret2026");
                pstmt.setString(3, "john@example.com");
                pstmt.executeUpdate();
            }

            System.out.println("Database ready. John Marshall added.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}