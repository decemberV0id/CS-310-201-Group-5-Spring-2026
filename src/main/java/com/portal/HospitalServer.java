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

        // ---------------- LOGIN ----------------
        app.post("/login", ctx -> {

            String username = ctx.formParam("username");
            String password = ctx.formParam("password");

            if (username == null || password == null) {
                ctx.result("Please fill username and password");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {

                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT password, role FROM Account WHERE user_name = ?"
                );
                stmt.setString(1, username);

                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String realPassword = rs.getString("password");

                    if (realPassword.equals(password)) { // NOTE: Hashing recommended
                        ctx.sessionAttribute("username", username);
                        ctx.sessionAttribute("role", rs.getString("role"));

                        ctx.redirect("/messages.html");
                        System.out.println("User " + username + " logged in");
                    } else {
                        ctx.result("<h2>Wrong password</h2>");
                    }
                } else {
                    ctx.result("<h2>User not found</h2>");
                }

            } catch (Exception e) {
                ctx.result("<h2>Database problem</h2>");
                e.printStackTrace();
            }
        });

        // ---------------- CALENDAR ----------------
        app.post("/calendar", ctx -> {

            String username = ctx.sessionAttribute("username");

            if (username == null) {
                ctx.status(401).result("{\"error\":\"Not logged in\"}");
                return;
            }

            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            String sql =
                    "SELECT a.event_date, a.event_time, a.description, a.doctorname " +
                    "FROM Appointment a " +
                    "JOIN PatientAccount p ON a.patient_account_id = p.patient_account_id " +
                    "WHERE p.user_name = ? " +
                    "ORDER BY a.event_date, a.event_time";

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db");
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, username);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String dateKey = rs.getString("event_date");
                    String time = rs.getString("event_time");
                    String doctor = rs.getString("doctorname");
                    String desc = rs.getString("description");

                    String eventText =
                            (time != null ? time : "") + " – " +
                            (doctor != null ? doctor : "Dr. Smith") + "<br>" +
                            (desc != null ? desc : "Appointment");

                    if (!first) json.append(",");
                    json.append("\"").append(dateKey).append("\":\"")
                            .append(eventText.replace("\"", "\\\""))
                            .append("\"");

                    first = false;
                }

            } catch (SQLException e) {
                ctx.status(500).result("{\"error\":\"Database error\"}");
                return;
            }

            json.append("}");
            ctx.result(json.toString());
        });

        // ---------------- REGISTER ----------------
        app.post("/register", ctx -> {

            String username = ctx.formParam("username");
            String password = ctx.formParam("password");
            String email = ctx.formParam("email");
            int age = Integer.parseInt(ctx.formParam("age"));
            String ssn = ctx.formParam("ssn");

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db")) {
                conn.setAutoCommit(false);

                try (
                        PreparedStatement insertAccount =
                                conn.prepareStatement("INSERT INTO Account VALUES (?, ?, ?, 'patient')");
                        PreparedStatement insertPatient =
                                conn.prepareStatement("INSERT INTO PatientAccount (user_name, age, ssn) VALUES (?, ?, ?)")
                ) {
                    insertAccount.setString(1, username);
                    insertAccount.setString(2, password);
                    insertAccount.setString(3, email);
                    insertAccount.executeUpdate();

                    insertPatient.setString(1, username);
                    insertPatient.setInt(2, age);
                    insertPatient.setString(3, ssn);
                    insertPatient.executeUpdate();

                    conn.commit();
                    ctx.result("Registration successful");

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        });

        // ---------------- SEND MESSAGE ----------------
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

            if (role.equalsIgnoreCase("patient")) {
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db");
                     PreparedStatement roleCheck =
                             conn.prepareStatement("SELECT role FROM Account WHERE user_name = ?")) {

                    roleCheck.setString(1, recipient);
                    ResultSet rs = roleCheck.executeQuery();

                    if (rs.next() && rs.getString("role").equalsIgnoreCase("patient")) {
                        ctx.status(403).result("Patients cannot message other patients");
                        return;
                    }
                }
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db");
                 PreparedStatement stmt =
                         conn.prepareStatement(
                                 "INSERT INTO Messages (sender_user_name, receiver_user_name, message_text) VALUES (?, ?, ?)")
            ) {

                stmt.setString(1, sender);
                stmt.setString(2, recipient);
                stmt.setString(3, body);
                stmt.executeUpdate();

                ctx.result("Message sent");

            } catch (SQLException e) {
                ctx.status(500).result("Database error");
            }
        });

        // ---------------- LOAD MESSAGES ----------------
        app.get("/messages/me", ctx -> {

            String username = ctx.sessionAttribute("username");

            if (username == null) {
                ctx.status(401).result("Not logged in");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hospital.db");
                 PreparedStatement stmt =
                         conn.prepareStatement(
                                 "SELECT sender_user_name, message_text, created_at " +
                                 "FROM Messages WHERE receiver_user_name = ? ORDER BY created_at DESC")) {

                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{")
                            .append("\"sender\":\"").append(rs.getString("sender_user_name")).append("\",")
                            .append("\"body\":\"").append(rs.getString("message_text").replace("\"", "\\\"")).append("\",")
                            .append("\"created_at\":\"").append(rs.getString("created_at")).append("\"")
                            .append("}");
                    first = false;
                }

                json.append("]");
                ctx.result(json.toString());

            } catch (SQLException e) {
                ctx.status(500).result("{\"error\":\"Database error\"}");
            }
        });
    }
}