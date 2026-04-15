package com.portal;

import io.javalin.Javalin;
import java.sql.*;


public class HospitalServer {
    public static String currentUser = "johnm";
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
                    "SELECT password, role FROM Account WHERE user_name = ?"
                );
                stmt.setString(1, username);

                // Execute the query and get the result
                ResultSet rs = stmt.executeQuery();

                // Check if a user was found and compare passwords
                if (rs.next()) {
                    String realPassword = rs.getString("password");

                    // Compare the provided password with the one from the database
                    if (realPassword.equals(password)) {
                        ctx.sessionAttribute("username", username);
                        ctx.sessionAttribute("role", rs.getString("role"));
                        currentUser = username;
                        ctx.result("<h1>Welcome, " + username + "!</h1>");
                        System.out.println("User " + username + " logged in with role " + rs.getString("role"));
                    } else {// Wrong password
                        ctx.result("<h2>Wrong password</h2>");
                    }
                } else {// No user found with the given username
                    ctx.result("<h2>User not found</h2>");
                }

            } catch (Exception e) {// Database connection or query error
                ctx.result("<h2>Database problem :(</h2>)");
            }
        });

        app.post("/calendar", ctx -> {

            String username = currentUser;

            if (username == null || username.trim().isEmpty()) {
                System.out.println("ERROR: No currentUser set!");
                ctx.status(401).result("{\"error\": \"No user logged in\"}");
                return;
            }

            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {

                // ==================== GET ROLE ====================
                String role = null;
                try (PreparedStatement roleStmt = conn.prepareStatement(
                        "SELECT role FROM Account WHERE user_name = ?")) {

                    roleStmt.setString(1, username);
                    try (ResultSet rs = roleStmt.executeQuery()) {
                        if (rs.next()) {
                            role = rs.getString("role");
                        }
                    }
                }

                if (role == null) {
                    ctx.status(404).result("{\"error\": \"User not found\"}");
                    return;
                }

                String sql;

                // ==================== DOCTOR VIEW ====================
                if ("doctor".equalsIgnoreCase(role)) {

                    sql = "SELECT a.event_date, a.event_time, a.description, " +
                        "p.first_name, p.last_name " +
                        "FROM Appointment a " +
                        "JOIN PatientAccount p ON a.patient_account_id = p.patient_account_id " +
                        "WHERE a.doctor_user_name = ? " +
                        "ORDER BY a.event_date, a.event_time";

                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

                        pstmt.setString(1, username);

                        try (ResultSet rs = pstmt.executeQuery()) {
                            while (rs.next()) {

                                String dateKey = rs.getString("event_date");
                                String time = rs.getString("event_time") != null ? rs.getString("event_time") : "";
                                String patientName = rs.getString("first_name") + " " + rs.getString("last_name");
                                String desc = rs.getString("description") != null ? rs.getString("description") : "Appointment";

                                // 🔥 doctor sees PATIENT name
                                String eventText = time + " – " + patientName + "<br>" + desc;

                                if (!first) json.append(",");
                                json.append("\"")
                                    .append(dateKey)
                                    .append("\":\"")
                                    .append(eventText.replace("\"", "\\\""))
                                    .append("\"");

                                first = false;
                            }
                        }
                    }

                } else {
                    // ==================== PATIENT VIEW ====================

                    sql = "SELECT a.event_date, a.event_time, a.description, a.doctorname " +
                        "FROM Appointment a " +
                        "JOIN PatientAccount p ON a.patient_account_id = p.patient_account_id " +
                        "WHERE p.user_name = ? " +
                        "ORDER BY a.event_date, a.event_time";

                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

                        pstmt.setString(1, username);

                        try (ResultSet rs = pstmt.executeQuery()) {
                            while (rs.next()) {

                                String dateKey = rs.getString("event_date");
                                String time = rs.getString("event_time") != null ? rs.getString("event_time") : "";
                                String doctor = rs.getString("doctorname") != null ? rs.getString("doctorname") : "Dr. Smith";
                                String desc = rs.getString("description") != null ? rs.getString("description") : "Appointment";

                                String eventText = time + " – " + doctor + "<br>" + desc;

                                if (!first) json.append(",");
                                json.append("\"")
                                    .append(dateKey)
                                    .append("\":\"")
                                    .append(eventText.replace("\"", "\\\""))
                                    .append("\"");

                                first = false;
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
                e.printStackTrace();
                ctx.status(500).result("{\"error\": \"Database error\"}");
                return;
            }

            json.append("}");

            ctx.result(json.toString());   // Send raw JSON string instead of ctx.json()
        });
        
        app.post("/messages", ctx -> {
            String username = currentUser;

            if (username == null || username.trim().isEmpty()) {
                ctx.status(401).result("No current user set");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                try (Statement schemaStmt = conn.createStatement()) {
                    schemaStmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS Messages (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            sender_user_name TEXT NOT NULL,
                            receiver_user_name TEXT NOT NULL,
                            message_text TEXT NOT NULL,
                            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                        )
                        """
                    );
                }

                boolean hasCreatedAt = false;
                try (PreparedStatement colStmt = conn.prepareStatement("PRAGMA table_info(Messages)");
                     ResultSet colRs = colStmt.executeQuery()) {
                    while (colRs.next()) {
                        String colName = colRs.getString("name");
                        if ("created_at".equalsIgnoreCase(colName)) {
                            hasCreatedAt = true;
                            break;
                        }
                    }
                }

                String selectSql;
                if (hasCreatedAt) {
                    selectSql =
                        "SELECT sender_user_name AS sender, " +
                        "receiver_user_name AS recipient, " +
                        "message_text, " +
                        "created_at " +
                        "FROM Messages " +
                        "WHERE sender_user_name = ? OR receiver_user_name = ? " +
                        "ORDER BY created_at ASC";
                } else {
                    selectSql =
                        "SELECT sender_user_name AS sender, " +
                        "receiver_user_name AS recipient, " +
                        "message_text, " +
                        "'' AS created_at " +
                        "FROM Messages " +
                        "WHERE sender_user_name = ? OR receiver_user_name = ?";
                }

                PreparedStatement stmt = conn.prepareStatement(selectSql);
                stmt.setString(1, username);
                stmt.setString(2, username);

                ResultSet rs = stmt.executeQuery();
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    String sender = rs.getString("sender");
                    if (sender == null) sender = "";
                    sender = sender.replace("\\", "\\\\")
                                   .replace("\"", "\\\"")
                                   .replace("\n", "\\n")
                                   .replace("\r", "\\r");

                    String recipient = rs.getString("recipient");
                    if (recipient == null) recipient = "";
                    recipient = recipient.replace("\\", "\\\\")
                                         .replace("\"", "\\\"")
                                         .replace("\n", "\\n")
                                         .replace("\r", "\\r");

                    String body = rs.getString("message_text");
                    if (body == null) body = "";
                    body = body.replace("\\", "\\\\")
                               .replace("\"", "\\\"")
                               .replace("\n", "\\n")
                               .replace("\r", "\\r");

                    String createdAt = rs.getString("created_at");
                    if (createdAt == null) createdAt = "";
                    createdAt = createdAt.replace("\\", "\\\\")
                                         .replace("\"", "\\\"")
                                         .replace("\n", "\\n")
                                         .replace("\r", "\\r");

                    json.append("{")
                        .append("\"sender\":\"").append(sender).append("\",")
                        .append("\"recipient\":\"").append(recipient).append("\",")
                        .append("\"body\":\"").append(body).append("\",")
                        .append("\"created_at\":\"").append(createdAt).append("\"")
                        .append("}");

                    first = false;
                }

                json.append("]");

                String safeUser = username.replace("\\", "\\\\")
                                          .replace("\"", "\\\"")
                                          .replace("\n", "\\n")
                                          .replace("\r", "\\r");

                String responseJson = "{\"currentUser\":\""
                    + safeUser
                    + "\",\"messages\":"
                    + json
                    + "}";

                ctx.contentType("application/json");
                ctx.result(responseJson);

                rs.close();
                stmt.close();

            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"currentUser\":\"\",\"messages\":[]}");
            }
        });
        

        
        app.post("/example", ctx -> {
             String username = currentUser;
            // Connect to database
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                //database query stuff





            } catch (Exception e) {// Database connection or query error
                ctx.result("<h2>Database problem :(</h2>)");//respond with error message
                return;
            }

            ctx.result("hi");//final response to html
        });

    
    }
}
