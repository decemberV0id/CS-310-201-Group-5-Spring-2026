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
                        FOREIGN KEY (patient_account_id) REFERENCES PatientAccount(patient_account_id)
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
                    CREATE TABLE IF NOT EXISTS Messages (
                        message_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sender_user_name TEXT NOT NULL,
                        receiver_user_name TEXT NOT NULL,
                        message_text TEXT NOT NULL,
                        sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        is_read BOOLEAN DEFAULT 0,
                        read_at DATETIME,
                        FOREIGN KEY (sender_user_name) REFERENCES Account(user_name),
                        FOREIGN KEY (receiver_user_name) REFERENCES Account(user_name)
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

            // 1. Insert johnm (patient)
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Account (user_name, email, password, role) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, "johnm");
                pstmt.setString(2, "john@example.com");
                pstmt.setString(3, "secret2026");
                pstmt.setString(4, "patient");
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO PatientAccount (user_name, age, ssn, height, first_name, last_name, previous_clinic, preconditions) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                pstmt.setString(1, "johnm");
                pstmt.setInt(2, 34);
                pstmt.setString(3, "123-45-6789");
                pstmt.setDouble(4, 175.5);
                pstmt.setString(5, "John");
                pstmt.setString(6, "Miller");
                pstmt.setString(7, "City General");
                pstmt.setString(8, "Hypertension");
                pstmt.executeUpdate();
            }

            // 2. Insert drsmith (doctor)
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Account (user_name, email, password, role) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, "drsmith");
                pstmt.setString(2, "dr.smith@hospital.com");
                pstmt.setString(3, "secret2026");
                pstmt.setString(4, "doctor");
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO StaffAccount (user_name, employeeid, position, address, specialty) " +
                    "VALUES (?, ?, ?, ?, ?)")) {
                pstmt.setString(1, "drsmith");
                pstmt.setString(2, "EMP001");
                pstmt.setString(3, "Cardiologist");
                pstmt.setString(4, "123 Medical Lane");
                pstmt.setString(5, "Cardiology");
                pstmt.executeUpdate();
            }

            // 3. Insert messages
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Messages (sender_user_name, receiver_user_name, message_text) VALUES (?, ?, ?)")) {

                pstmt.setString(1, "johnm");
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Hello Dr. Smith, I have been feeling dizzy lately. Can we schedule an appointment?");
                pstmt.executeUpdate();

                pstmt.setString(1, "drsmith");
                pstmt.setString(2, "johnm");
                pstmt.setString(3, "Hi John, thanks for reaching out. I reviewed your recent vitals. Let's meet next week.");
                pstmt.executeUpdate();
            }

            // 4. Insert 3 test results for johnm
            int patientId = getPatientAccountId(conn, "johnm");

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO LabTestResult " +
                    "(patient_account_id, test_name, lab_name, lab_results, test_date, notes) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {

                pstmt.setInt(1, patientId);
                pstmt.setString(2, "Complete Blood Count (CBC)");
                pstmt.setString(3, "Main Lab");
                pstmt.setString(4, "WBC: 7.2, RBC: 4.8, Hemoglobin: 14.2");
                pstmt.setString(5, "2026-03-15 09:30:00");
                pstmt.setString(6, "All values within normal range");
                pstmt.executeUpdate();

                pstmt.setInt(1, patientId);
                pstmt.setString(2, "Lipid Panel");
                pstmt.setString(3, "Main Lab");
                pstmt.setString(4, "Total Cholesterol: 198, LDL: 112, HDL: 52, Triglycerides: 145");
                pstmt.setString(5, "2026-03-20 10:15:00");
                pstmt.setString(6, "Slightly elevated LDL - recommend dietary changes");
                pstmt.executeUpdate();

                pstmt.setInt(1, patientId);
                pstmt.setString(2, "Blood Pressure Monitoring");
                pstmt.setString(3, "Clinic Lab");
                pstmt.setString(4, "Average: 138/88 mmHg");
                pstmt.setString(5, "2026-04-05 08:45:00");
                pstmt.setString(6, "Stage 1 Hypertension confirmed");
                pstmt.executeUpdate();
            }

            // 5. Insert 3 Appointments in April for John Marshall
            int patientAccountId = getPatientAccountId(conn, "johnm");

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Appointment " +
                    "(patient_account_id, doctor_user_name, type, doctorname, event_date, event_time, duration_minutes, description) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

                // Appointment 1 - April 10
                pstmt.setInt(1, patientAccountId);
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Follow-up");
                pstmt.setString(4, "Dr. Smith");
                pstmt.setString(5, "2026-04-10");
                pstmt.setString(6, "10:00");
                pstmt.setInt(7, 30);
                pstmt.setString(8, "Monthly hypertension check-up");
                pstmt.executeUpdate();

                // Appointment 2 - April 18
                pstmt.setInt(1, patientAccountId);
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Lab Review");
                pstmt.setString(4, "Dr. Smith");
                pstmt.setString(5, "2026-04-18");
                pstmt.setString(6, "14:30");
                pstmt.setInt(7, 45);
                pstmt.setString(8, "Review recent blood test results");
                pstmt.executeUpdate();

                // Appointment 3 - April 25
                pstmt.setInt(1, patientAccountId);
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Consultation");
                pstmt.setString(4, "Dr. Smith");
                pstmt.setString(5, "2026-04-25");
                pstmt.setString(6, "09:15");
                pstmt.setInt(7, 30);
                pstmt.setString(8, "Discuss treatment plan and medication adjustment");
                pstmt.executeUpdate();
            }

            // ==================== NEW: Second patient (janed) ====================

            // 6. Insert janed (patient) - new patient account
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Account (user_name, email, password, role) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, "janed");
                pstmt.setString(2, "jane.doe@example.com");
                pstmt.setString(3, "secret2026");
                pstmt.setString(4, "patient");
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO PatientAccount (user_name, age, ssn, height, first_name, last_name, previous_clinic, preconditions) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                pstmt.setString(1, "janed");
                pstmt.setInt(2, 28);
                pstmt.setString(3, "987-65-4321");
                pstmt.setDouble(4, 162.3);
                pstmt.setString(5, "Jane");
                pstmt.setString(6, "Doe");
                pstmt.setString(7, "None");
                pstmt.setString(8, "Asthma");
                pstmt.executeUpdate();
            }

            // 7. Insert 4 test results for janed
            int janePatientId = getPatientAccountId(conn, "janed");

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO LabTestResult " +
                    "(patient_account_id, test_name, lab_name, lab_results, test_date, notes) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {

                // Test 1 - April 1
                pstmt.setInt(1, janePatientId);
                pstmt.setString(2, "Spirometry");
                pstmt.setString(3, "Pulmonary Lab");
                pstmt.setString(4, "FEV1: 85% predicted, FVC: 90% predicted");
                pstmt.setString(5, "2026-04-01 10:00:00");
                pstmt.setString(6, "Mild obstructive pattern consistent with asthma");
                pstmt.executeUpdate();

                // Test 2 - April 3
                pstmt.setInt(1, janePatientId);
                pstmt.setString(2, "Allergy Panel");
                pstmt.setString(3, "Main Lab");
                pstmt.setString(4, "Positive for dust mites and pollen");
                pstmt.setString(5, "2026-04-03 11:30:00");
                pstmt.setString(6, "Environmental triggers identified");
                pstmt.executeUpdate();

                // Test 3 - April 10
                pstmt.setInt(1, janePatientId);
                pstmt.setString(2, "Complete Blood Count (CBC)");
                pstmt.setString(3, "Main Lab");
                pstmt.setString(4, "WBC: 6.5, RBC: 4.5, Hemoglobin: 13.8");
                pstmt.setString(5, "2026-04-10 09:00:00");
                pstmt.setString(6, "Normal results");
                pstmt.executeUpdate();

                // Test 4 - April 12
                pstmt.setInt(1, janePatientId);
                pstmt.setString(2, "Chest X-Ray");
                pstmt.setString(3, "Radiology");
                pstmt.setString(4, "No acute abnormalities, mild hyperinflation");
                pstmt.setString(5, "2026-04-12 14:00:00");
                pstmt.setString(6, "Consistent with asthma");
                pstmt.executeUpdate();
            }

            // 8. Insert 1 Appointment in April for Jane Doe
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Appointment " +
                    "(patient_account_id, doctor_user_name, type, doctorname, event_date, event_time, duration_minutes, description) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

                pstmt.setInt(1, janePatientId);
                pstmt.setString(2, "drsmith");
                pstmt.setString(3, "Consultation");
                pstmt.setString(4, "Dr. Smith");
                pstmt.setString(5, "2026-04-22");
                pstmt.setString(6, "11:00");
                pstmt.setInt(7, 45);
                pstmt.setString(8, "Initial consultation for asthma symptoms and management plan");
                pstmt.executeUpdate();
            }

            // 9. Insert medications for both patients (johnm + janed)
            int johnPatientIdM = getPatientAccountId(conn, "johnm");
            int janePatientIdM = getPatientAccountId(conn, "janed");

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO Medication " +
                    "(patient_account_id, medicine_name, frequency, date_prescribed, notes) " +
                    "VALUES (?, ?, ?, ?, ?)")) {

                // Medications for John Miller (hypertension focus)
                pstmt.setInt(1, johnPatientIdM);
                pstmt.setString(2, "Lisinopril");
                pstmt.setString(3, "Once daily");
                pstmt.setString(4, "2026-03-10");
                pstmt.setString(5, "For hypertension management - 10mg");
                pstmt.executeUpdate();

                pstmt.setInt(1, johnPatientIdM);
                pstmt.setString(2, "Atorvastatin");
                pstmt.setString(3, "Once daily at bedtime");
                pstmt.setString(4, "2026-03-20");
                pstmt.setString(5, "For cholesterol management - 20mg");
                pstmt.executeUpdate();

                // Medications for Jane Doe (asthma focus)
                pstmt.setInt(1, janePatientIdM);
                pstmt.setString(2, "Fluticasone (Flovent)");
                pstmt.setString(3, "Twice daily");
                pstmt.setString(4, "2026-04-05");
                pstmt.setString(5, "Daily controller inhaler - 110mcg");
                pstmt.executeUpdate();

                pstmt.setInt(1, janePatientIdM);
                pstmt.setString(2, "Albuterol Inhaler");
                pstmt.setString(3, "As needed for symptoms");
                pstmt.setString(4, "2026-04-05");
                pstmt.setString(5, "Rescue inhaler - 2 puffs every 4-6 hours PRN");
                pstmt.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Helper method to get patient_account_id
    private static int getPatientAccountId(Connection conn, String userName) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT patient_account_id FROM PatientAccount WHERE user_name = ?")) {
            pstmt.setString(1, userName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("patient_account_id");
                }
            }
        }
        return -1;
    }
}