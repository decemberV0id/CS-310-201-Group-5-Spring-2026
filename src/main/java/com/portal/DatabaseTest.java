package com.portal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseTest {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:hospital.db";
        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("SQLite connection successful! Driver loaded.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}