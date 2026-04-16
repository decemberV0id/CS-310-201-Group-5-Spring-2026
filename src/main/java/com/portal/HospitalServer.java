package com.portal;

import io.javalin.Javalin;
import java.sql.*;
import java.util.Random;


public class HospitalServer {
    public static String currentUser = "johnm";
    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        }).start(7070);

        System.out.println("Server is running!");
        System.out.println("Go to: http://localhost:7070/login.html");

        // POST /login — Authenticates a user by checking username and password against the database.
        // Sets the current user on success and returns a welcome message.
        app.post("/login", ctx -> {

            // Read submitted credentials from the login form.

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

        // POST /verify — Returns the role and email of the current user.
        // For doctors, also generates a random 4-digit verification code.
        app.post("/verify", ctx -> {
            // Use the active server-side user as the verification subject.
            String username = currentUser;

            if (username == null || username.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"No user logged in\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT role, email FROM Account WHERE user_name = ?"
                );
                stmt.setString(1, username);

                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    ctx.status(404).contentType("application/json")
                       .result("{\"error\":\"User not found\"}");
                    return;
                }

                String role = rs.getString("role");
                String email = rs.getString("email") == null ? "unknown@example.com" : rs.getString("email");
                String safeRole = role.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                String safeEmail = email.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                if ("doctor".equalsIgnoreCase(role)) {
                    // Doctors receive an extra verification code and email payload.
                    int code = 1000 + new Random().nextInt(9000);

                    String payload = "{"
                        + "\"role\":\"" + safeRole + "\","
                        + "\"code\":\"" + code + "\","
                        + "\"email\":\"" + safeEmail + "\""
                        + "}";

                    ctx.contentType("application/json").result(payload);
                } else {
                    String payload = "{"
                        + "\"role\":\"" + safeRole + "\""
                        + "}";

                    ctx.contentType("application/json").result(payload);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database error\"}");
            }
        });

        // POST /calendar — Returns appointments for the current user as a JSON map of date → event text.
        // Doctors see patient names; patients see their doctor's name.
        app.post("/calendar", ctx -> {

            // Resolve current account before loading calendar events.
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

                    // Doctor timeline includes patient names per appointment.

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
                    // Patient timeline includes doctor name per appointment.

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
        
        // POST /messages — Returns all messages sent to or from the current user as a JSON array.
        app.post("/messages", ctx -> {
            // Fetch conversation history for the logged-in user.
            String username = currentUser;

            if (username == null || username.trim().isEmpty()) {
                ctx.status(401).result("No current user set");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                String selectSql =
                    "SELECT sender_user_name AS sender, " +
                    "receiver_user_name AS recipient, " +
                    "message_text, " +
                    "created_at, " +
                    "is_read " +
                    "FROM Messages " +
                    "WHERE sender_user_name = ? OR receiver_user_name = ? " +
                    "ORDER BY created_at ASC";

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

                    int isRead = rs.getInt("is_read");

                    json.append("{")
                        .append("\"sender\":\"").append(sender).append("\",")
                        .append("\"recipient\":\"").append(recipient).append("\",")
                        .append("\"body\":\"").append(body).append("\",")
                        .append("\"created_at\":\"").append(createdAt).append("\",")
                        .append("\"is_read\":").append(isRead)
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

        // GET /overview — Returns a dashboard summary for the current user:
        // display name, unread message count, active medication count, and upcoming appointments.
        app.get("/overview", ctx -> {
            // Build personalized overview cards and upcoming visit previews.
            String username = currentUser;


            if (username == null || username.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"No user logged in\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                String role = null;
                try (PreparedStatement roleStmt = conn.prepareStatement(
                    "SELECT role FROM Account WHERE user_name = ?"
                )) {
                    roleStmt.setString(1, username);
                    try (ResultSet roleRs = roleStmt.executeQuery()) {
                        if (roleRs.next()) {
                            role = roleRs.getString("role");
                        }
                    }
                }

                if (role == null) {
                    ctx.status(404).contentType("application/json")
                       .result("{\"error\":\"User not found\"}");
                    return;
                }

                String displayName = username;
                if ("patient".equalsIgnoreCase(role)) {
                    try (PreparedStatement nameStmt = conn.prepareStatement(
                        "SELECT first_name, last_name FROM PatientAccount WHERE user_name = ?"
                    )) {
                        nameStmt.setString(1, username);
                        try (ResultSet nameRs = nameStmt.executeQuery()) {
                            if (nameRs.next()) {
                                String firstName = nameRs.getString("first_name") == null ? "" : nameRs.getString("first_name");
                                String lastName = nameRs.getString("last_name") == null ? "" : nameRs.getString("last_name");
                                String fullName = (firstName + " " + lastName).trim();
                                if (!fullName.isEmpty()) {
                                    displayName = fullName;
                                }
                            }
                        }
                    }
                }

                int unreadCount = 0;
                try (PreparedStatement unreadStmt = conn.prepareStatement(
                    "SELECT COUNT(*) AS unread_count FROM Messages WHERE receiver_user_name = ? AND is_read = 0"
                )) {
                    unreadStmt.setString(1, username);
                    try (ResultSet unreadRs = unreadStmt.executeQuery()) {
                        if (unreadRs.next()) {
                            unreadCount = unreadRs.getInt("unread_count");
                        }
                    }
                }

                int activeMedsCount = 0;
                if ("doctor".equalsIgnoreCase(role)) {
                    // Count medications for patients assigned to this doctor.
                    try (PreparedStatement medsStmt = conn.prepareStatement(
                        "SELECT COUNT(DISTINCT m.medication_id) AS meds_count " +
                        "FROM Medication m " +
                        "JOIN Appointment a ON a.patient_account_id = m.patient_account_id " +
                        "WHERE a.doctor_user_name = ?"
                    )) {
                        medsStmt.setString(1, username);
                        try (ResultSet medsRs = medsStmt.executeQuery()) {
                            if (medsRs.next()) {
                                activeMedsCount = medsRs.getInt("meds_count");
                            }
                        }
                    }
                } else {
                    // Count medications for the currently logged-in patient.
                    try (PreparedStatement medsStmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS meds_count " +
                        "FROM Medication m " +
                        "JOIN PatientAccount p ON p.patient_account_id = m.patient_account_id " +
                        "WHERE p.user_name = ?"
                    )) {
                        medsStmt.setString(1, username);
                        try (ResultSet medsRs = medsStmt.executeQuery()) {
                            if (medsRs.next()) {
                                activeMedsCount = medsRs.getInt("meds_count");
                            }
                        }
                    }
                }

                int upcomingCount = 0;
                if ("doctor".equalsIgnoreCase(role)) {
                    // Doctors get their future appointments directly by doctor username.
                    try (PreparedStatement countStmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS appointment_count " +
                        "FROM Appointment " +
                        "WHERE doctor_user_name = ? AND date(event_date) >= date('now')"
                    )) {
                        countStmt.setString(1, username);
                        try (ResultSet countRs = countStmt.executeQuery()) {
                            if (countRs.next()) {
                                upcomingCount = countRs.getInt("appointment_count");
                            }
                        }
                    }
                } else {
                    // Patients get future appointments through their patient account mapping.
                    try (PreparedStatement countStmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS appointment_count " +
                        "FROM Appointment a " +
                        "JOIN PatientAccount p ON p.patient_account_id = a.patient_account_id " +
                        "WHERE p.user_name = ? AND date(a.event_date) >= date('now')"
                    )) {
                        countStmt.setString(1, username);
                        try (ResultSet countRs = countStmt.executeQuery()) {
                            if (countRs.next()) {
                                upcomingCount = countRs.getInt("appointment_count");
                            }
                        }
                    }
                }

                StringBuilder appointmentsJson = new StringBuilder("[");
                boolean firstAppointment = true;

                if ("doctor".equalsIgnoreCase(role)) {
                    try (PreparedStatement apptStmt = conn.prepareStatement(
                        "SELECT a.event_date, a.event_time, a.type, a.description, p.first_name, p.last_name " +
                        "FROM Appointment a " +
                        "JOIN PatientAccount p ON p.patient_account_id = a.patient_account_id " +
                        "WHERE a.doctor_user_name = ? AND date(a.event_date) >= date('now') " +
                        "ORDER BY a.event_date, a.event_time LIMIT 5"
                    )) {
                        apptStmt.setString(1, username);

                        try (ResultSet apptRs = apptStmt.executeQuery()) {
                            while (apptRs.next()) {
                                String date = apptRs.getString("event_date") == null ? "" : apptRs.getString("event_date");
                                String time = apptRs.getString("event_time") == null ? "" : apptRs.getString("event_time");
                                String specialty = apptRs.getString("type") == null ? "" : apptRs.getString("type");
                                String firstName = apptRs.getString("first_name") == null ? "" : apptRs.getString("first_name");
                                String lastName = apptRs.getString("last_name") == null ? "" : apptRs.getString("last_name");
                                String doctor = (firstName + " " + lastName).trim();
                                if (doctor.isEmpty()) {
                                    doctor = "Patient";
                                }

                                String action = "Review";
                                String description = apptRs.getString("description");
                                if (description != null && description.toLowerCase().contains("video")) {
                                    action = "Video Visit";
                                }

                                date = date.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                time = time.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                specialty = specialty.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                doctor = doctor.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                action = action.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                                if (!firstAppointment) {
                                    appointmentsJson.append(",");
                                }

                                appointmentsJson.append("{")
                                    .append("\"doctor\":\"").append(doctor).append("\",")
                                    .append("\"specialty\":\"").append(specialty).append("\",")
                                    .append("\"date\":\"").append(date).append("\",")
                                    .append("\"time\":\"").append(time).append("\",")
                                    .append("\"action\":\"").append(action).append("\"")
                                    .append("}");

                                firstAppointment = false;
                            }
                        }
                    }
                } else {
                    try (PreparedStatement apptStmt = conn.prepareStatement(
                        "SELECT a.event_date, a.event_time, a.type, a.description, a.doctorname, a.doctor_user_name " +
                        "FROM Appointment a " +
                        "JOIN PatientAccount p ON p.patient_account_id = a.patient_account_id " +
                        "WHERE p.user_name = ? AND date(a.event_date) >= date('now') " +
                        "ORDER BY a.event_date, a.event_time LIMIT 5"
                    )) {
                        apptStmt.setString(1, username);

                        try (ResultSet apptRs = apptStmt.executeQuery()) {
                            while (apptRs.next()) {
                                String date = apptRs.getString("event_date") == null ? "" : apptRs.getString("event_date");
                                String time = apptRs.getString("event_time") == null ? "" : apptRs.getString("event_time");
                                String specialty = apptRs.getString("type") == null ? "" : apptRs.getString("type");
                                String doctor = apptRs.getString("doctorname");
                                if (doctor == null || doctor.trim().isEmpty()) {
                                    doctor = apptRs.getString("doctor_user_name");
                                }
                                if (doctor == null || doctor.trim().isEmpty()) {
                                    doctor = "Doctor";
                                }

                                String action = "Review";
                                String description = apptRs.getString("description");
                                if (description != null && description.toLowerCase().contains("video")) {
                                    action = "Video Visit";
                                }

                                date = date.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                time = time.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                specialty = specialty.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                doctor = doctor.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                action = action.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                                if (!firstAppointment) {
                                    appointmentsJson.append(",");
                                }

                                appointmentsJson.append("{")
                                    .append("\"doctor\":\"").append(doctor).append("\",")
                                    .append("\"specialty\":\"").append(specialty).append("\",")
                                    .append("\"date\":\"").append(date).append("\",")
                                    .append("\"time\":\"").append(time).append("\",")
                                    .append("\"action\":\"").append(action).append("\"")
                                    .append("}");

                                firstAppointment = false;
                            }
                        }
                    }
                }

                appointmentsJson.append("]");

                displayName = displayName.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                String payload = "{" +
                    "\"userName\":\"" + displayName + "\"," +
                    "\"upcomingCount\":" + upcomingCount + "," +
                    "\"unreadCount\":" + unreadCount + "," +
                    "\"activeMedsCount\":" + activeMedsCount + "," +
                    "\"appointments\":" + appointmentsJson +
                    "}";

                ctx.contentType("application/json").result(payload);
            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database error\"}");
            }
        });
        
        // GET /profile — Returns profile details for the current user.
        // Patients get personal info (name, age, height, address, emergency contact).
        // Doctors get professional info (position, specialty, address).
        app.get("/profile", ctx -> {
            // Determine current user role, then project role-specific profile fields.
            String username = currentUser;

            if (username == null || username.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"No user logged in\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                String role = null;
                String phoneNumber = "";
                try (PreparedStatement roleStmt = conn.prepareStatement(
                        "SELECT role, contact_number FROM Account WHERE user_name = ?")) {
                    roleStmt.setString(1, username);
                    try (ResultSet roleRs = roleStmt.executeQuery()) {
                        if (roleRs.next()) {
                            role = roleRs.getString("role");
                            phoneNumber = roleRs.getString("contact_number") == null ? "" : roleRs.getString("contact_number");
                        }
                    }
                }

                if (role == null) {
                    ctx.status(404).contentType("application/json")
                       .result("{\"error\":\"User not found\"}");
                    return;
                }

                if ("patient".equalsIgnoreCase(role)) {
                    // Return patient demographics and emergency contact details.
                    String sql = "SELECT first_name, last_name, age, height, address, emergency_contact " +
                        "FROM PatientAccount WHERE user_name = ?";

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (!rs.next()) {
                                ctx.status(404).contentType("application/json")
                                   .result("{\"error\":\"Patient profile not found\"}");
                                return;
                            }

                            String firstName = rs.getString("first_name");
                            String lastName = rs.getString("last_name");
                            String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
                            if (name.isEmpty()) {
                                name = username;
                            }

                            String age = rs.getObject("age") == null ? "" : String.valueOf(rs.getObject("age"));
                            String height = rs.getObject("height") == null ? "" : String.valueOf(rs.getObject("height"));
                            String address = rs.getString("address") == null ? "" : rs.getString("address");
                            String emergencyContact = rs.getString("emergency_contact") == null ? "" : rs.getString("emergency_contact");

                            name = name.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            role = role.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            username = username.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            age = age.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            height = height.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            address = address.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            emergencyContact = emergencyContact.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            phoneNumber = phoneNumber.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                            String json = "{" +
                                "\"username\":\"" + username + "\"," +
                                "\"name\":\"" + name + "\"," +
                                "\"role\":\"" + role + "\"," +
                                "\"age\":\"" + age + "\"," +
                                "\"high\":\"" + height + "\"," +
                                "\"height\":\"" + height + "\"," +
                                "\"phoneNumber\":\"" + phoneNumber + "\"," +
                                "\"address\":\"" + address + "\"," +
                                "\"emergencyContact\":\"" + emergencyContact + "\"" +
                                "}";

                            ctx.contentType("application/json").result(json);
                        }
                    }
                } else if ("doctor".equalsIgnoreCase(role)) {
                    // Return doctor-facing professional profile fields.
                    String sql = "SELECT position, specialty, address FROM StaffAccount WHERE user_name = ?";

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (!rs.next()) {
                                ctx.status(404).contentType("application/json")
                                   .result("{\"error\":\"Doctor profile not found\"}");
                                return;
                            }

                            String name = username;
                            String position = rs.getString("position") == null ? "" : rs.getString("position");
                            String specialty = rs.getString("specialty") == null ? "" : rs.getString("specialty");
                            String address = rs.getString("address") == null ? "" : rs.getString("address");

                            name = name.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            role = role.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            username = username.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            position = position.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            specialty = specialty.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            address = address.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            phoneNumber = phoneNumber.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                            String json = "{" +
                                "\"username\":\"" + username + "\"," +
                                "\"name\":\"" + name + "\"," +
                                "\"role\":\"" + role + "\"," +
                                "\"position\":\"" + position + "\"," +
                                "\"specialty\":\"" + specialty + "\"," +
                                "\"phoneNumber\":\"" + phoneNumber + "\"," +
                                "\"address\":\"" + address + "\"" +
                                "}";

                            ctx.contentType("application/json").result(json);
                        }
                    }
                } else {
                    ctx.status(400).contentType("application/json")
                       .result("{\"error\":\"Unsupported role\"}");
                }

            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database error\"}");
            }
        });

        // POST /updateprofile — Updates a single editable field on the current user's profile.
        // Patients can edit: address, emergencyContact, phoneNumber.
        // Doctors can edit: address, phoneNumber.
        app.post("/updateprofile", ctx -> {
            // Apply a single-field profile update based on role-specific rules.
            String username = currentUser;

            if (username == null || username.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"No user logged in\"}");
                return;
            }

            String field = ctx.formParam("field");
            String value = ctx.formParam("value");

            if (field == null || value == null) {
                ctx.status(400).contentType("application/json")
                   .result("{\"error\":\"Missing field or value\"}");
                return;
            }

            field = field.trim();
            value = value.trim();

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
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
                    ctx.status(404).contentType("application/json")
                       .result("{\"error\":\"User not found\"}");
                    return;
                }

                int updated = 0;
                if ("patient".equalsIgnoreCase(role)) {
                    // Patient edit whitelist.
                    if ("address".equals(field)) {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "UPDATE PatientAccount SET address = ? WHERE user_name = ?")) {
                            stmt.setString(1, value);
                            stmt.setString(2, username);
                            updated = stmt.executeUpdate();
                        }
                    } else if ("emergencyContact".equals(field)) {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "UPDATE PatientAccount SET emergency_contact = ? WHERE user_name = ?")) {
                            stmt.setString(1, value);
                            stmt.setString(2, username);
                            updated = stmt.executeUpdate();
                        }
                    } else if ("phoneNumber".equals(field)) {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "UPDATE Account SET contact_number = ? WHERE user_name = ?")) {
                            stmt.setString(1, value);
                            stmt.setString(2, username);
                            updated = stmt.executeUpdate();
                        }
                    } else {
                        ctx.status(400).contentType("application/json")
                           .result("{\"error\":\"Field not editable\"}");
                        return;
                    }
                } else if ("doctor".equalsIgnoreCase(role)) {
                    // Doctor edit whitelist.
                    if ("address".equals(field)) {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "UPDATE StaffAccount SET address = ? WHERE user_name = ?")) {
                            stmt.setString(1, value);
                            stmt.setString(2, username);
                            updated = stmt.executeUpdate();
                        }
                    } else if ("phoneNumber".equals(field)) {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "UPDATE Account SET contact_number = ? WHERE user_name = ?")) {
                            stmt.setString(1, value);
                            stmt.setString(2, username);
                            updated = stmt.executeUpdate();
                        }
                    } else {
                        ctx.status(400).contentType("application/json")
                           .result("{\"error\":\"Field not editable\"}");
                        return;
                    }
                }

                if (updated == 0) {
                    ctx.status(404).contentType("application/json")
                       .result("{\"error\":\"Profile row not found\"}");
                    return;
                }

                ctx.contentType("application/json").result("{\"success\":true}");
            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database error\"}");
            }
        });

        // GET /reports — Returns all lab test results for the current patient, ordered by date.
        app.get("/reports", ctx -> {
            // Resolve the user role and stream lab results as JSON.
            String user = currentUser;

            if (user == null || user.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"No user logged in\"}");
                return;
            }

            // Connect to the database for role lookup and report queries.
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                String role = null;

                // Get the current user's role.
                try (PreparedStatement roleStmt = conn.prepareStatement(
                        "SELECT role FROM Account WHERE user_name = ?")) {
                    roleStmt.setString(1, user);
                    try (ResultSet roleRs = roleStmt.executeQuery()) {
                        if (roleRs.next()) {
                            role = roleRs.getString("role");
                        }
                    }
                }

                // Doctors see tests they ordered; patients see their own tests.
                boolean doctorView = "doctor".equalsIgnoreCase(role);
                String sql;

                if (doctorView) {
                    sql = "SELECT pa.patient_account_id, pa.first_name, pa.last_name, " +
                        "ltr.test_name, ltr.lab_results, ltr.test_date, ltr.notes " +
                        "FROM LabTestResult ltr " +
                        "JOIN PatientAccount pa ON pa.patient_account_id = ltr.patient_account_id " +
                        "WHERE ltr.ordered_by = ? " +
                        "ORDER BY ltr.test_date DESC";
                } else {
                    sql = "SELECT pa.patient_account_id, pa.first_name, pa.last_name, " +
                        "ltr.test_name, ltr.lab_results, ltr.test_date, ltr.notes " +
                        "FROM PatientAccount pa " +
                        "JOIN LabTestResult ltr ON pa.patient_account_id = ltr.patient_account_id " +
                        "WHERE pa.user_name = ? " +
                        "ORDER BY ltr.test_date DESC";
                }

                // Run the query for this user and build the JSON response.
                try (PreparedStatement getReports = conn.prepareStatement(sql)) {
                    getReports.setString(1, user);

                    try (ResultSet rs = getReports.executeQuery()) {
                        // Start with an empty patient object and append results rows below.
                        StringBuilder json = new StringBuilder();
                        json.append("{\"patient\":{\"id\":\"\",\"name\":\"\"},\"results\":[");

                        boolean first = true;
                        String patientId = "";
                        String patientName = "";

                        // Convert each DB row into one JSON result object.
                        while (rs.next()) {
                            if (!first) json.append(",");
                            first = false;

                            String rowPatientName = "";
                            String firstName = rs.getString("first_name") == null ? "" : rs.getString("first_name");
                            String lastName = rs.getString("last_name") == null ? "" : rs.getString("last_name");
                            rowPatientName = (firstName + " " + lastName).trim();

                            if (!doctorView && patientId.isEmpty()) {
                                patientId = String.valueOf(rs.getInt("patient_account_id"));
                                patientName = rowPatientName;
                            }

                            String testName = rs.getString("test_name") == null ? "" : rs.getString("test_name");
                            String status = rs.getString("lab_results") == null ? "" : rs.getString("lab_results");
                            String date = rs.getString("test_date") == null ? "" : rs.getString("test_date");
                            String notes = rs.getString("notes") == null ? "" : rs.getString("notes");

                            // Escape values since this endpoint builds JSON manually.
                            testName = testName.replace("\\", "\\\\").replace("\"", "\\\"");
                            status = status.replace("\\", "\\\\").replace("\"", "\\\"");
                            date = date.replace("\\", "\\\\").replace("\"", "\\\"");
                            notes = notes.replace("\\", "\\\\").replace("\"", "\\\"");
                            rowPatientName = rowPatientName.replace("\\", "\\\\").replace("\"", "\\\"");

                            json.append("{\"test_name\":\"")
                                .append(testName)
                                .append("\",\"status\":\"")
                                .append(status)
                                .append("\",\"date\":\"")
                                .append(date)
                                .append("\",\"notes\":\"")
                                .append(notes)
                                .append("\"");

                            if (doctorView) {
                                json.append(",\"patient_name\":\"")
                                    .append(rowPatientName)
                                    .append("\"");
                            }

                            json.append("}");
                        }

                        // Fill top-level patient info for patient view.
                        String safePatientId = patientId.replace("\\", "\\\\").replace("\"", "\\\"");
                        String safePatientName = patientName.replace("\\", "\\\\").replace("\"", "\\\"");

                        String payload = json.toString();
                        payload = payload.replace(
                            "{\"patient\":{\"id\":\"\",\"name\":\"\"}",
                            "{\"patient\":{\"id\":\"" + safePatientId + "\",\"name\":\"" + safePatientName + "\"}"
                        );

                        // Close and send the response payload.
                        payload = payload + "]}";

                        ctx.contentType("application/json").result(payload);
                    }
                }
            } catch (SQLException e) {
                // Return a generic error when database operations fail.
                System.out.println("Database error: " + e.getMessage());
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database error\"}");
            }
        });

        // POST /messages/send — Saves a new message from the current user to a specified recipient.
        // Returns the saved message as JSON.
        app.post("/messages/send", ctx -> {
            // Validate sender/recipient and persist a new message row.
            String sender = currentUser;
            if (sender == null || sender.trim().isEmpty()) {
                sender = currentUser;
            }

            if (sender == null || sender.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"Not logged in\"}");
                return;
            }

            String recipient = ctx.formParam("recipient");
            String body = ctx.formParam("body");

            if (recipient == null || body == null || recipient.trim().isEmpty() || body.trim().isEmpty()) {
                ctx.status(400).contentType("application/json")
                   .result("{\"error\":\"Invalid recipient or message\"}");
                return;
            }

            recipient = recipient.trim();
            body = body.trim();

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO Messages (sender_user_name, receiver_user_name, message_text, is_read) VALUES (?, ?, ?, ?)"
                )) {
                    stmt.setString(1, sender);
                    stmt.setString(2, recipient);
                    stmt.setString(3, body);
                    stmt.setInt(4, 0);
                    stmt.executeUpdate();
                }

                String createdAt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date());

                String msgEscaped = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                String responseJson = "{\"sender\":\"" + sender + "\",\"recipient\":\"" + recipient + "\",\"body\":\"" + msgEscaped + "\",\"created_at\":\"" + createdAt + "\",\"is_read\":0}";

                ctx.status(200).contentType("application/json").result(responseJson);

            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database error\"}");
            }
        });

        // POST /messages/read — Marks all messages from a given sender as read for the current user.
        app.post("/messages/read", ctx -> {
            // Mark inbound messages from a specific conversation partner as read.
            String username = currentUser;

            if (username == null || username.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"Not logged in\"}");
                return;
            }

            String otherUser = ctx.formParam("otherUser");

            if (otherUser == null || otherUser.trim().isEmpty()) {
                ctx.status(400).contentType("application/json")
                   .result("{\"error\":\"Missing otherUser parameter\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE Messages SET is_read = 1 WHERE receiver_user_name = ? AND sender_user_name = ?"
                )) {
                    stmt.setString(1, username);
                    stmt.setString(2, otherUser);
                    stmt.executeUpdate();
                }

                ctx.status(200).contentType("application/json")
                   .result("{\"status\":\"success\"}");

            } catch (SQLException e) {
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database error\"}");
            }
        });

        // GET /medications — Returns medication data for the current user.
        // Doctors see all medications for their patients; patients see only their own.
        app.get("/medications", ctx -> {
            // Branch response shape by role (doctor roster vs patient list).
            String username = currentUser;
            if (username == null || username.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"No user logged in\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                String role = null;
                try (PreparedStatement roleStmt = conn.prepareStatement(
                        "SELECT role FROM Account WHERE user_name = ?")) {
                    roleStmt.setString(1, username);
                    try (ResultSet roleRs = roleStmt.executeQuery()) {
                        if (roleRs.next()) {
                            role = roleRs.getString("role");
                        }
                    }
                }

                if (role == null) {
                    ctx.status(404).contentType("application/json")
                       .result("{\"error\":\"User not found\"}");
                    return;
                }

                if ("doctor".equalsIgnoreCase(role)) {
                    // Build grouped patient -> medications payload for the doctor's panel.
                    String sql = "SELECT pa.patient_account_id, pa.first_name, pa.last_name, " +
                        "m.medicine_name, m.frequency, m.date_prescribed, m.notes " +
                        "FROM (SELECT DISTINCT patient_account_id FROM Appointment WHERE doctor_user_name = ?) ap " +
                        "JOIN PatientAccount pa ON pa.patient_account_id = ap.patient_account_id " +
                        "LEFT JOIN Medication m ON m.patient_account_id = pa.patient_account_id " +
                        "ORDER BY pa.last_name, pa.first_name, m.date_prescribed DESC, m.medicine_name";

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);

                        try (ResultSet rs = stmt.executeQuery()) {
                            StringBuilder json = new StringBuilder();
                            String safeUsername = username.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            json.append("{\"role\":\"doctor\",\"doctor\":{\"username\":\"")
                                .append(safeUsername)
                                .append("\"},\"patients\":[");

                            boolean firstPatient = true;
                            int currentPatientId = -1;
                            boolean firstMedicationForPatient = true;

                            while (rs.next()) {
                                int patientId = rs.getInt("patient_account_id");
                                String firstName = rs.getString("first_name") == null ? "" : rs.getString("first_name");
                                String lastName = rs.getString("last_name") == null ? "" : rs.getString("last_name");
                                String fullName = (firstName + " " + lastName).trim();
                                if (fullName.isEmpty()) {
                                    fullName = "Patient " + patientId;
                                }
                                String safePatientId = String.valueOf(patientId).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                String safeFullName = fullName.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                                if (patientId != currentPatientId) {
                                    if (currentPatientId != -1) {
                                        json.append("]}");
                                    }

                                    if (!firstPatient) {
                                        json.append(",");
                                    }

                                    json.append("{\"id\":\"")
                                        .append(safePatientId)
                                        .append("\",\"name\":\"")
                                        .append(safeFullName)
                                        .append("\",\"medications\":[");

                                    firstPatient = false;
                                    currentPatientId = patientId;
                                    firstMedicationForPatient = true;
                                }

                                String medicineName = rs.getString("medicine_name");
                                if (medicineName != null) {
                                    String frequency = rs.getString("frequency") == null ? "" : rs.getString("frequency");
                                    String datePrescribed = rs.getString("date_prescribed") == null ? "" : rs.getString("date_prescribed");
                                    String notes = rs.getString("notes") == null ? "" : rs.getString("notes");
                                    String safeMedicineName = medicineName.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                    String safeFrequency = frequency.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                    String safeDatePrescribed = datePrescribed.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                    String safeNotes = notes.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                                    if (!firstMedicationForPatient) {
                                        json.append(",");
                                    }

                                    json.append("{\"medicine_name\":\"")
                                        .append(safeMedicineName)
                                        .append("\",\"frequency\":\"")
                                        .append(safeFrequency)
                                        .append("\",\"date_prescribed\":\"")
                                        .append(safeDatePrescribed)
                                        .append("\",\"notes\":\"")
                                        .append(safeNotes)
                                        .append("\"}");

                                    firstMedicationForPatient = false;
                                }
                            }

                            if (currentPatientId != -1) {
                                json.append("]}");
                            }

                            json.append("]}");

                            ctx.contentType("application/json").result(json.toString());
                        }
                    }
                } else {
                    // Build a single patient medication list for self view.
                    String sql = "SELECT pa.patient_account_id, pa.first_name, pa.last_name, " +
                        "m.medicine_name, m.frequency, m.date_prescribed, m.notes " +
                        "FROM PatientAccount pa " +
                        "LEFT JOIN Medication m ON pa.patient_account_id = m.patient_account_id " +
                        "WHERE pa.user_name = ? " +
                        "ORDER BY m.date_prescribed DESC, m.medicine_name";

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);

                        try (ResultSet rs = stmt.executeQuery()) {
                            StringBuilder json = new StringBuilder();
                            json.append("{\"role\":\"patient\",\"patient\":{\"id\":\"\",\"name\":\"\"},\"medications\":[");

                            boolean firstMedication = true;
                            boolean foundPatient = false;
                            String patientId = "";
                            String patientName = "";

                            while (rs.next()) {
                                if (!foundPatient) {
                                    foundPatient = true;
                                    patientId = String.valueOf(rs.getInt("patient_account_id"));

                                    String firstName = rs.getString("first_name") == null ? "" : rs.getString("first_name");
                                    String lastName = rs.getString("last_name") == null ? "" : rs.getString("last_name");
                                    patientName = (firstName + " " + lastName).trim();
                                    if (patientName.isEmpty()) {
                                        patientName = username;
                                    }
                                }

                                String medicineName = rs.getString("medicine_name");
                                if (medicineName == null) {
                                    continue;
                                }

                                String frequency = rs.getString("frequency") == null ? "" : rs.getString("frequency");
                                String datePrescribed = rs.getString("date_prescribed") == null ? "" : rs.getString("date_prescribed");
                                String notes = rs.getString("notes") == null ? "" : rs.getString("notes");
                                String safeMedicineName = medicineName.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                String safeFrequency = frequency.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                String safeDatePrescribed = datePrescribed.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                String safeNotes = notes.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");

                                if (!firstMedication) {
                                    json.append(",");
                                }

                                json.append("{\"medicine_name\":\"")
                                    .append(safeMedicineName)
                                    .append("\",\"frequency\":\"")
                                    .append(safeFrequency)
                                    .append("\",\"date_prescribed\":\"")
                                    .append(safeDatePrescribed)
                                    .append("\",\"notes\":\"")
                                    .append(safeNotes)
                                    .append("\"}");

                                firstMedication = false;
                            }

                            if (!foundPatient) {
                                ctx.status(404).contentType("application/json")
                                   .result("{\"error\":\"Patient profile not found\"}");
                                return;
                            }

                            String payload = json.toString();
                            String safePatientId = patientId.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            String safePatientName = patientName.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            payload = payload.replace(
                                "{\"role\":\"patient\",\"patient\":{\"id\":\"\",\"name\":\"\"}",
                                "{\"role\":\"patient\",\"patient\":{\"id\":\"" + safePatientId + "\",\"name\":\"" + safePatientName + "\"}"
                            );

                            payload = payload + "]}";
                            ctx.contentType("application/json").result(payload);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database problem\"}");
            }
        });

        // POST /example — Template/placeholder endpoint for testing new database queries.
        app.post("/example", ctx -> {
            // Placeholder handler keeps DB connection scaffold for future experiments.
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
