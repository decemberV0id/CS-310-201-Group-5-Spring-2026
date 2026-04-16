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
                            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                            is_read INTEGER DEFAULT 0
                        )
                        """
                    );
                }

                boolean hasCreatedAt = false;
                boolean hasIsRead = false;
                try (PreparedStatement colStmt = conn.prepareStatement("PRAGMA table_info(Messages)");
                     ResultSet colRs = colStmt.executeQuery()) {
                    while (colRs.next()) {
                        String colName = colRs.getString("name");
                        if ("created_at".equalsIgnoreCase(colName)) {
                            hasCreatedAt = true;
                        }
                        if ("is_read".equalsIgnoreCase(colName)) {
                            hasIsRead = true;
                        }
                    }
                }

                String selectSql;
                if (hasCreatedAt && hasIsRead) {
                    selectSql =
                        "SELECT sender_user_name AS sender, " +
                        "receiver_user_name AS recipient, " +
                        "message_text, " +
                        "created_at, " +
                        "is_read " +
                        "FROM Messages " +
                        "WHERE sender_user_name = ? OR receiver_user_name = ? " +
                        "ORDER BY created_at ASC";
                } else if (hasCreatedAt) {
                    selectSql =
                        "SELECT sender_user_name AS sender, " +
                        "receiver_user_name AS recipient, " +
                        "message_text, " +
                        "created_at, " +
                        "0 AS is_read " +
                        "FROM Messages " +
                        "WHERE sender_user_name = ? OR receiver_user_name = ? " +
                        "ORDER BY created_at ASC";
                } else {
                    selectSql =
                        "SELECT sender_user_name AS sender, " +
                        "receiver_user_name AS recipient, " +
                        "message_text, " +
                        "'' AS created_at, " +
                        "0 AS is_read " +
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
        
        app.get("/profile", ctx -> {
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

        app.post("/updateprofile", ctx -> {
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

        app.get("/reports", ctx -> {
            String user = currentUser;

            if (user == null || user.trim().isEmpty()) {
                ctx.status(401).contentType("application/json")
                   .result("{\"error\":\"No user logged in\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                String sql = "SELECT pa.patient_account_id, pa.first_name, pa.last_name, " +
                    "ltr.test_name, ltr.lab_results, ltr.test_date, ltr.notes " +
                    "FROM PatientAccount pa " +
                    "JOIN LabTestResult ltr ON pa.patient_account_id = ltr.patient_account_id " +
                    "WHERE pa.user_name = ? " +
                    "ORDER BY ltr.test_date DESC";

                try (PreparedStatement getReports = conn.prepareStatement(sql)) {
                    getReports.setString(1, user);

                    try (ResultSet rs = getReports.executeQuery()) {
                        StringBuilder json = new StringBuilder();
                        json.append("{\"patient\":{\"id\":\"\",\"name\":\"\"},\"results\":[");

                        boolean first = true;
                        String patientId = "";
                        String patientName = "";

                        while (rs.next()) {
                            if (!first) json.append(",");
                            first = false;

                            if (patientId.isEmpty()) {
                                patientId = String.valueOf(rs.getInt("patient_account_id"));
                                String firstName = rs.getString("first_name") == null ? "" : rs.getString("first_name");
                                String lastName = rs.getString("last_name") == null ? "" : rs.getString("last_name");
                                patientName = (firstName + " " + lastName).trim();
                            }

                            String testName = rs.getString("test_name") == null ? "" : rs.getString("test_name");
                            String status = rs.getString("lab_results") == null ? "" : rs.getString("lab_results");
                            String date = rs.getString("test_date") == null ? "" : rs.getString("test_date");
                            String notes = rs.getString("notes") == null ? "" : rs.getString("notes");

                            testName = testName.replace("\\", "\\\\").replace("\"", "\\\"");
                            status = status.replace("\\", "\\\\").replace("\"", "\\\"");
                            date = date.replace("\\", "\\\\").replace("\"", "\\\"");
                            notes = notes.replace("\\", "\\\\").replace("\"", "\\\"");

                            json.append(String.format(
                                "{\"test_name\":\"%s\",\"status\":\"%s\",\"date\":\"%s\",\"notes\":\"%s\"}",
                                testName, status, date, notes
                            ));
                        }

                        String safePatientId = patientId.replace("\\", "\\\\").replace("\"", "\\\"");
                        String safePatientName = patientName.replace("\\", "\\\\").replace("\"", "\\\"");

                        String payload = json.toString();
                        int patientStart = payload.indexOf("{\"patient\":{\"id\":\"\",\"name\":\"\"}");
                        if (patientStart >= 0) {
                            payload = payload.replace(
                                "{\"patient\":{\"id\":\"\",\"name\":\"\"}",
                                "{\"patient\":{\"id\":\"" + safePatientId + "\",\"name\":\"" + safePatientName + "\"}"
                            );
                        }

                        payload = payload + "]}";

                        ctx.contentType("application/json").result(payload);
                    }
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
                e.printStackTrace();
                ctx.status(500).contentType("application/json")
                   .result("{\"error\":\"Database error\"}");
            }
        });

        app.post("/messages/send", ctx -> {
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

        app.get("/medications", ctx -> {
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
