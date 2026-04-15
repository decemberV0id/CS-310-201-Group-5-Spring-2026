package com.portal;

import java.sql.*;

public class DatabaseInitializer {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:hospital.db";

        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Connected to SQLite database");

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");

                // Create tables
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Account (
                        user_name TEXT PRIMARY KEY,
                        email TEXT UNIQUE,
                        password TEXT NOT NULL,
                        contact_number TEXT,
                        role TEXT NOT NULL CHECK (role IN ('patient', 'doctor')),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS PatientAccount (
                        patient_account_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_name TEXT UNIQUE NOT NULL,
                        age INTEGER,
                        ssn TEXT UNIQUE,
                        height REAL,
                        first_name TEXT,
                        last_name TEXT,
                        previous_clinic TEXT,
                        preconditions TEXT,
                        FOREIGN KEY (user_name) REFERENCES Account(user_name)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS StaffAccount (
                        staff_account_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_name TEXT UNIQUE NOT NULL,
                        employeeid TEXT UNIQUE,
                        position TEXT,
                        address TEXT,
                        specialty TEXT,
                        FOREIGN KEY (user_name) REFERENCES Account(user_name)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS PatientChart (
                        patientchart_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patient_account_id INTEGER NOT NULL,
                        blood_pressure TEXT,
                        weight REAL,
                        temperature REAL,
                        balance TEXT,
                        drug_prescription TEXT,
                        labtest_results TEXT,
                        notes TEXT,
                        recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        recorded_by INTEGER,
                        FOREIGN KEY (patient_account_id) REFERENCES PatientAccount(patient_account_id),
                        FOREIGN KEY (recorded_by) REFERENCES StaffAccount(staff_account_id)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS LabTestResult (
                        labtestresult_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patient_account_id INTEGER NOT NULL,
                        test_name TEXT NOT NULL,
                        lab_name TEXT,
                        lab_results TEXT,
                        test_date DATETIME NOT NULL,
                        notes TEXT,
                        ordered_by INTEGER,
                        FOREIGN KEY (patient_account_id) REFERENCES PatientAccount(patient_account_id)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE Messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sender_user_name   TEXT NOT NULL,   -- patient or provider
                        receiver_user_name TEXT NOT NULL,
                        message_text       TEXT NOT NULL,
                        created_at         DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Appointment (
                        appointment_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patient_account_id INTEGER NOT NULL,
                        doctor_user_name TEXT NOT NULL,
                        type TEXT,
                        doctorname TEXT,
                        event_date DATE NOT NULL,
                        event_time TIME NOT NULL,
                        duration_minutes INTEGER,
                        description TEXT NOT NULL,
                        status TEXT DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'completed', 'cancelled')),
                        FOREIGN KEY (patient_account_id) REFERENCES PatientAccount(patient_account_id),
                        FOREIGN KEY (doctor_user_name) REFERENCES Account(user_name)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Medication (
                        medication_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patient_account_id INTEGER NOT NULL,
                        medicine_name TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        date_prescribed DATE NOT NULL,
                        notes TEXT,
                        FOREIGN KEY (patient_account_id) REFERENCES PatientAccount(patient_account_id)
                    )
                """);
            }

            // ==================== INSERT SAMPLE DATA ====================

            // johnm
            insertAccount(conn, "johnm", "john@example.com", "patient");
            insertPatient(conn, "johnm", 34, "123-45-6789", 175.5, "John", "Miller", "City General", "Hypertension");

            // drsmith
            insertAccount(conn, "drsmith", "dr.smith@hospital.com", "doctor");
            insertStaff(conn, "drsmith", "EMP001", "Cardiologist", "123 Medical Lane", "Cardiology");

            // janed
            insertAccount(conn, "janed", "jane.doe@example.com", "patient");
            insertPatient(conn, "janed", 28, "987-65-4321", 162.3, "Jane", "Doe", "None", "Asthma");

            int johnId = getPatientAccountId(conn, "johnm");
            int janeId = getPatientAccountId(conn, "janed");

            // ==================== MESSAGES ====================
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Messages (sender_user_name, receiver_user_name, message_text) VALUES (?, ?, ?)")) {

                pstmt.setString(1, "johnm");
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Hello Dr. Smith, I have been feeling dizzy lately. Can we schedule an appointment?");
                pstmt.executeUpdate();

                pstmt.setString(1, "drsmith");
                pstmt.setString(2, "johnm");
                pstmt.setString(3, "Hi John, thanks for reaching out. Let's meet next week.");
                pstmt.executeUpdate();
            }

            // ==================== LAB TESTS ====================
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO LabTestResult (patient_account_id, test_name, lab_name, lab_results, test_date, notes) VALUES (?, ?, ?, ?, ?, ?)")) {

                // John (3 tests)
                pstmt.setInt(1, johnId);
                pstmt.setString(2, "Complete Blood Count (CBC)");
                pstmt.setString(3, "Main Lab");
                pstmt.setString(4, "Normal");
                pstmt.setString(5, "2026-03-15 09:30:00");
                pstmt.setString(6, "All values normal");
                pstmt.executeUpdate();

                pstmt.setInt(1, johnId);
                pstmt.setString(2, "Lipid Panel");
                pstmt.setString(3, "Main Lab");
                pstmt.setString(4, "LDL slightly high");
                pstmt.setString(5, "2026-03-20 10:15:00");
                pstmt.setString(6, "Diet recommended");
                pstmt.executeUpdate();

                pstmt.setInt(1, johnId);
                pstmt.setString(2, "Blood Pressure Monitoring");
                pstmt.setString(3, "Clinic Lab");
                pstmt.setString(4, "138/88 avg");
                pstmt.setString(5, "2026-04-05 08:45:00");
                pstmt.setString(6, "Hypertension stage 1");
                pstmt.executeUpdate();

                // Jane (4 tests)
                pstmt.setInt(1, janeId);
                pstmt.setString(2, "Spirometry");
                pstmt.setString(3, "Pulmonary Lab");
                pstmt.setString(4, "Mild obstruction");
                pstmt.setString(5, "2026-04-01");
                pstmt.setString(6, "Asthma");
                pstmt.executeUpdate();

                pstmt.setInt(1, janeId);
                pstmt.setString(2, "Allergy Panel");
                pstmt.setString(3, "Main Lab");
                pstmt.setString(4, "Dust + pollen");
                pstmt.setString(5, "2026-04-03");
                pstmt.setString(6, "Triggers found");
                pstmt.executeUpdate();

                pstmt.setInt(1, janeId);
                pstmt.setString(2, "CBC");
                pstmt.setString(3, "Main Lab");
                pstmt.setString(4, "Normal");
                pstmt.setString(5, "2026-04-10");
                pstmt.setString(6, "Healthy");
                pstmt.executeUpdate();

                pstmt.setInt(1, janeId);
                pstmt.setString(2, "Chest X-Ray");
                pstmt.setString(3, "Radiology");
                pstmt.setString(4, "Mild hyperinflation");
                pstmt.setString(5, "2026-04-12");
                pstmt.setString(6, "Asthma-related");
                pstmt.executeUpdate();
            }

            // ==================== APPOINTMENTS ====================
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO Appointment (patient_account_id, doctor_user_name, type, doctorname, event_date, event_time, duration_minutes, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

                // John (3 appointments)
                pstmt.setInt(1, johnId);
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Follow-up");
                pstmt.setString(4, "Dr. Smith");
                pstmt.setString(5, "2026-04-10");
                pstmt.setString(6, "10:00");
                pstmt.setInt(7, 30);
                pstmt.setString(8, "Monthly check-up");
                pstmt.executeUpdate();

                pstmt.setInt(1, johnId);
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Lab Review");
                pstmt.setString(4, "Dr. Smith");
                pstmt.setString(5, "2026-04-18");
                pstmt.setString(6, "14:30");
                pstmt.setInt(7, 45);
                pstmt.setString(8, "Review labs");
                pstmt.executeUpdate();

                pstmt.setInt(1, johnId);
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Consultation");
                pstmt.setString(4, "Dr. Smith");
                pstmt.setString(5, "2026-04-25");
                pstmt.setString(6, "09:15");
                pstmt.setInt(7, 30);
                pstmt.setString(8, "Treatment plan");
                pstmt.executeUpdate();

                // Jane (1 appointment)
                pstmt.setInt(1, janeId);
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Consultation");
                pstmt.setString(4, "Dr. Smith");
                pstmt.setString(5, "2026-04-22");
                pstmt.setString(6, "11:00");
                pstmt.setInt(7, 45);
                pstmt.setString(8, "Asthma consult");
                pstmt.executeUpdate();
            }

            // ==================== MEDICATION ====================
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO Medication (patient_account_id, medicine_name, frequency, date_prescribed, notes) VALUES (?, ?, ?, ?, ?)")) {

                pstmt.setInt(1, johnId);
                pstmt.setString(2, "Lisinopril");
                pstmt.setString(3, "Daily");
                pstmt.setString(4, "2026-03-10");
                pstmt.setString(5, "Hypertension");
                pstmt.executeUpdate();

                pstmt.setInt(1, johnId);
                pstmt.setString(2, "Atorvastatin");
                pstmt.setString(3, "Nightly");
                pstmt.setString(4, "2026-03-20");
                pstmt.setString(5, "Cholesterol");
                pstmt.executeUpdate();

                pstmt.setInt(1, janeId);
                pstmt.setString(2, "Fluticasone");
                pstmt.setString(3, "Twice daily");
                pstmt.setString(4, "2026-04-05");
                pstmt.setString(5, "Controller inhaler");
                pstmt.executeUpdate();

                pstmt.setInt(1, janeId);
                pstmt.setString(2, "Albuterol");
                pstmt.setString(3, "As needed");
                pstmt.setString(4, "2026-04-05");
                pstmt.setString(5, "Rescue inhaler");
                pstmt.executeUpdate();
            }

            // ==================== PATIENT CHART ====================
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO PatientChart (patient_account_id, blood_pressure, weight, temperature, balance, drug_prescription, labtest_results, notes, recorded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                // John
                pstmt.setInt(1, johnId);
                pstmt.setString(2, "140/90");
                pstmt.setDouble(3, 85.2);
                pstmt.setDouble(4, 98.6);
                pstmt.setString(5, "Outstanding");
                pstmt.setString(6, "Lisinopril");
                pstmt.setString(7, "Elevated BP");
                pstmt.setString(8, "Dizziness");
                pstmt.setInt(9, 1);
                pstmt.executeUpdate();

                // Jane
                pstmt.setInt(1, janeId);
                pstmt.setString(2, "120/78");
                pstmt.setDouble(3, 60.5);
                pstmt.setDouble(4, 98.7);
                pstmt.setString(5, "Paid");
                pstmt.setString(6, "Inhaler");
                pstmt.setString(7, "Mild asthma");
                pstmt.setString(8, "Breathing issues");
                pstmt.setInt(9, 1);
                pstmt.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== HELPERS ====================

    private static void insertAccount(Connection conn, String user, String email, String role) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO Account (user_name, email, password, role) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, user);
            pstmt.setString(2, email);
            pstmt.setString(3, "secret2026");
            pstmt.setString(4, role);
            pstmt.executeUpdate();
        }
    }

    private static void insertPatient(Connection conn, String user, int age, String ssn, double height,
                                      String first, String last, String clinic, String conditions) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO PatientAccount VALUES (NULL, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setString(1, user);
            pstmt.setInt(2, age);
            pstmt.setString(3, ssn);
            pstmt.setDouble(4, height);
            pstmt.setString(5, first);
            pstmt.setString(6, last);
            pstmt.setString(7, clinic);
            pstmt.setString(8, conditions);
            pstmt.executeUpdate();
        }
    }

    private static void insertStaff(Connection conn, String user, String empId,
                                    String position, String address, String specialty) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO StaffAccount VALUES (NULL, ?, ?, ?, ?, ?)")) {
            pstmt.setString(1, user);
            pstmt.setString(2, empId);
            pstmt.setString(3, position);
            pstmt.setString(4, address);
            pstmt.setString(5, specialty);
            pstmt.executeUpdate();
        }
    }

    private static int getPatientAccountId(Connection conn, String userName) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT patient_account_id FROM PatientAccount WHERE user_name = ?")) {
            pstmt.setString(1, userName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }
}
