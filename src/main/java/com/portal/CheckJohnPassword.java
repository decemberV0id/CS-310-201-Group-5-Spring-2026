package com.portal;

import java.sql.*;

public class CheckJohnPassword {
    private static final String DB_URL = "jdbc:sqlite:hospital.db";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT user_name, password FROM Account WHERE user_name = ?")) {

            stmt.setString(1, "johnm");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                System.out.println("Found user: " + rs.getString("user_name"));
                System.out.println("Stored password: '" + rs.getString("password") + "'");
                System.out.println("Try typing exactly that (no extra spaces)");
            } else {
                System.out.println("No user with username 'johnm' exists in Account table");
            }

        } catch (SQLException e) {
            System.err.println("DB error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}