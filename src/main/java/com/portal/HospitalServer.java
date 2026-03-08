package com.portal;

import io.javalin.Javalin;
import java.sql.*;

public class HospitalServer {

    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        }).start(7070);

        System.out.println("Server is running!");
        System.out.println("Go to: http://localhost:7070/login.html");

        app.post("/login", ctx -> {

            String username = ctx.formParam("username");
            String password = ctx.formParam("password");

            // If something is missing → show error
            if (username == null || password == null) {
                ctx.result("Please fill username and password");
                return;
            }

            // Connect to database
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {

                // Prepare SQL statement to get the password for the given username
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT password FROM Account WHERE user_name = ?"
                );
                stmt.setString(1, username);

                // Execute the query and get the result
                ResultSet rs = stmt.executeQuery();

                // Check if a user was found and compare passwords
                if (rs.next()) {
                    String realPassword = rs.getString("password");

                    // Compare the provided password with the one from the database
                    if (realPassword.equals(password)) {
                        ctx.result("<h1>Welcome, " + username + "!</h1><p>You are logged in!</p>");
                    } else {// Wrong password
                        ctx.result("<h2>Wrong password</h2>");
                    }
                } else {// No user found with the given username
                    ctx.result("<h2>User not found</h2>");
                }

            } catch (Exception e) {// Database connection or query error
                ctx.result("<h2>Database problem :(</h2>");
            }
        });
    }
}