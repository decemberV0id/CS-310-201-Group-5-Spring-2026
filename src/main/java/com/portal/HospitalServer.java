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
                ctx.result("<h2>Database problem :(</h2>");
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
            int count = 0;

            String sql = "SELECT a.event_date, a.event_time, a.description, a.doctorname " +
                         "FROM Appointment a " +
                         "JOIN PatientAccount p ON a.patient_account_id = p.patient_account_id " +
                         "WHERE p.user_name = ? " +
                         "ORDER BY a.event_date, a.event_time";

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db");
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, username);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String dateKey = rs.getString("event_date");
                        String time = rs.getString("event_time") != null ? rs.getString("event_time") : "";
                        String doctor = rs.getString("doctorname") != null ? rs.getString("doctorname") : "Dr. Smith";
                        String desc = rs.getString("description") != null ? rs.getString("description") : "Appointment";

                        String eventText = time + " – " + doctor + "<br>" + desc;

                        if (!first) json.append(",");
                        json.append("\"").append(dateKey).append("\":\"").append(eventText.replace("\"", "\\\"")).append("\"");

                        first = false;
                        count++;
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



        app.post("/register", ctx -> {
            String username = ctx.formParam("username");
            String hashedPw = ctx.formParam("password");
            String email = ctx.formParam("email");
            int age = Integer.parseInt(ctx.formParam("age"));
            String ssn = ctx.formParam("ssn");
            //String url = "jdbc:sqlite:hospital.db";

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                conn.setAutoCommit(false);
                try (
                    PreparedStatement insertAccount = conn.prepareStatement(
                        "INSERT INTO Account (user_name, password, email) VALUES (?, ?, ?)");
                    PreparedStatement insertPatient = conn.prepareStatement(
                        "INSERT INTO PatientAccount (user_name, age, ssn) VALUES (?, ?, ?)")
                ) {
                    insertAccount.setString(1, username);
                    insertAccount.setString(2, hashedPw);
                    insertAccount.setString(3, email);
                    insertAccount.executeUpdate();

                    insertPatient.setString(1, username);
                    insertPatient.setInt(2, age);
                    insertPatient.setString(3, ssn);
                    insertPatient.executeUpdate();

                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            }
        });

        app.post("/updatePatient", ctx -> {
            String username = ctx.formParam("username");
            String password = ctx.formParam("password");
            String email = ctx.formParam("email");
            String ageStr = ctx.formParam("age");
            String ssn = ctx.formParam("ssn");

            if (username == null || username.isBlank()) {
                ctx.result("Username is required");
                return;
            }

            Integer age = null;
            if (ageStr != null && !ageStr.isBlank()) {
                try {
                    age = Integer.parseInt(ageStr);
                } catch (NumberFormatException e) {
                    ctx.result("Age must be a number");
                    return;
                }
            }

            try {
                boolean updated = updatePatientAccount(username, password, email, age, ssn);
                if (updated) {
                    ctx.result("<h2>Patient info updated successfully</h2>");
                } else {
                    ctx.result("<h2>User not found or update failed</h2>");
                }
            } catch (SQLException ex) {
                ctx.result("<h2>Database problem: " + ex.getMessage() + "</h2>");
            }
        });
        
        app.get("/messages/{username}", ctx -> {
            String targetUsername = ctx.pathParam("username");
            String loggedInUser = ctx.sessionAttribute("username");
            String role = ctx.sessionAttribute("role");

            if (role == null || loggedInUser == null) {
                ctx.status(401).result("Not logged in");
                return;
            }

            // Patients can only see their own messages
            if (role.equalsIgnoreCase("patient") && !targetUsername.equals(loggedInUser)) {
                ctx.status(403).result("Access denied");
                return;
            }

            if (!hasRole(ctx, "doctor", "nurse", "admin", "patient")) {
                ctx.status(403).result("Access denied");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db");
                PreparedStatement stmt = conn.prepareStatement(
                    """
                    SELECT
                        sender_user_name,
                        receiver_user_name,
                        message_text,
                        sent_at
                    FROM Messages
                    WHERE sender_user_name = ?
                        OR receiver_user_name = ?
                    ORDER BY sent_at DESC
                    """
                )) {

                stmt.setString(1, targetUsername);
                stmt.setString(2, targetUsername);

                ResultSet rs = stmt.executeQuery();

                var messages = new java.util.ArrayList<java.util.Map<String, String>>();

                while (rs.next()) {
                    var msg = new java.util.HashMap<String, String>();
                    msg.put("sender", rs.getString("sender_user_name"));
                    msg.put("body", rs.getString("message_text"));
                    msg.put("created_at", rs.getString("sent_at"));
                    messages.add(msg);
                }

                ctx.json(messages);

            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).result("Database error");
            }
        });
        
        app.post("/messages", ctx -> {
            String sender = ctx.sessionAttribute("username");
            String role = ctx.sessionAttribute("role");

            if (sender == null || role == null) {
                ctx.status(401).result("Not logged in");
                return;
            }

            String recipient = ctx.formParam("recipient");
            String body = ctx.formParam("body");

            if (recipient == null || body == null || body.isBlank()) {
                ctx.status(400).result("Invalid message data");
                return;
            }

            // Patients cannot message other patients
            if (role.equalsIgnoreCase("patient")) {
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db");
                    PreparedStatement roleCheck = conn.prepareStatement(
                        "SELECT role FROM Account WHERE user_name = ?"
                    )) {

                    roleCheck.setString(1, recipient);
                    ResultSet rs = roleCheck.executeQuery();

                    if (rs.next() && rs.getString("role").equalsIgnoreCase("patient")) {
                        ctx.status(403).result("Patients cannot message other patients");
                        return;
                    }
                }
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db");
                PreparedStatement stmt = conn.prepareStatement(
                    """
                    INSERT INTO Messages
                    (sender_user_name, receiver_user_name, message_text)
                    VALUES (?, ?, ?)
                    """
                )) {

                stmt.setString(1, sender);
                stmt.setString(2, recipient);
                stmt.setString(3, body);
                stmt.executeUpdate();

                ctx.result("Message sent");

            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).result("Database error");
            }
        });
    }

    private static boolean updatePatientAccount(String username, String password, String email, Integer age, String ssn) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
            conn.setAutoCommit(false);

            // user must already exist
            try (PreparedStatement checkUser = conn.prepareStatement("SELECT 1 FROM Account WHERE user_name = ?")) {
                checkUser.setString(1, username);
                try (ResultSet rs = checkUser.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                }
            }

            // update account fields with values provided (nullable)
            try (PreparedStatement updateAccount = conn.prepareStatement(
                    "UPDATE Account SET password = COALESCE(?, password), email = COALESCE(?, email) WHERE user_name = ?")) {
                updateAccount.setString(1, password);
                updateAccount.setString(2, email);
                updateAccount.setString(3, username);
                updateAccount.executeUpdate();
            }

            // upsert patient account data
            try (PreparedStatement upsertPatient = conn.prepareStatement(
                    "INSERT INTO PatientAccount (user_name, age, ssn) VALUES (?, ?, ?) " +
                    "ON CONFLICT(user_name) DO UPDATE SET age = COALESCE(excluded.age, PatientAccount.age), ssn = COALESCE(excluded.ssn, PatientAccount.ssn)")) {
                upsertPatient.setString(1, username);
                if (age != null) {
                    upsertPatient.setInt(2, age);
                } else {
                    upsertPatient.setNull(2, java.sql.Types.INTEGER);
                }
                upsertPatient.setString(3, ssn);
                upsertPatient.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException ex) {
            // don't hide what happened; caller can decide to show error
            throw ex;
        }
    }
    private static boolean hasRole(io.javalin.http.Context ctx, String... allowedRoles) {
        String role = ctx.sessionAttribute("role");
        if (role == null) return false;

        for (String allowed : allowedRoles) {
            if (role.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }
}
