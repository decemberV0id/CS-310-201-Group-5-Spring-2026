package com.portal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;

public class LoginTest {

    private static final String DB_URL = "jdbc:sqlite:hospital.db";

    @BeforeEach
    void setup() throws SQLException {
        // Make sure a test user exists before every test
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT OR IGNORE INTO Account (user_name, password) VALUES (?, ?)")) {

            stmt.setString(1, "testuser");
            stmt.setString(2, "testpass123");
            stmt.executeUpdate();
        }
    }

    @Test
    void correctCredentialsShouldAllowLogin() throws SQLException {
        boolean canLogin = checkCredentials("testuser", "testpass123");
        assertTrue(canLogin, "Correct username + password should succeed");
    }

    @Test
    void wrongPasswordShouldDenyLogin() throws SQLException {
        boolean canLogin = checkCredentials("testuser", "wrongpass");
        assertFalse(canLogin, "Wrong password should fail");
    }

    @Test
    void wrongUsernameShouldDenyLogin() throws SQLException {
        boolean canLogin = checkCredentials("nonexistent", "testpass123");
        assertFalse(canLogin, "Wrong username should fail");
    }

    @Test
    void unknownUserShouldFail() throws SQLException {
        boolean canLogin = checkCredentials("nonexistent", "anything");
        assertFalse(canLogin, "User that does not exist should fail");
    }

    // Helper method – same logic your server uses
    private boolean checkCredentials(String username, String password) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT password FROM Account WHERE user_name = ?")) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedPassword = rs.getString("password");
                return storedPassword.equals(password);
            }
            return false;
        }
    }
}