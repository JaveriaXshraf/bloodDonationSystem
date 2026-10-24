// ============================================================
//  DatabaseHelper.java  –  MySQL via JDBC + Stored Procedures
//  Schema: blood_bank.sql (import manually or auto on init)
//  All CRUD uses CALL sp_* procedures; multi-step ops use
//  sp_fulfill_request_with_donation (transaction inside MySQL).
// ============================================================

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper {

    private static final String HOST     = "localhost";
    private static final String PORT     = "3306";
    private static final String DATABASE = "blood_bank";
    private static final String USER     = "root";
    private static final String PASSWORD = "";

    private static final String SQL_FILE = "blood_bank.sql";

    private static final String DB_URL =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    private DatabaseHelper() {}

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASSWORD);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  INIT – database, tables, procedures (from blood_bank.sql logic)
    // ─────────────────────────────────────────────────────────────────────
    public static void initDB() {
        // #region agent log
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            DebugLog.write("H1", "DatabaseHelper.initDB", "MySQL driver class loaded", "{}");
        } catch (ClassNotFoundException e) {
            DebugLog.write("H1", "DatabaseHelper.initDB", "MySQL driver class NOT on classpath",
                    "{\"jarHint\":\"mysql-connector.jar\"}");
        }
        // #endregion
        String urlNoDB = "jdbc:mysql://" + HOST + ":" + PORT
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(urlNoDB, USER, PASSWORD);
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DATABASE
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            System.out.println("[DB] Database '" + DATABASE + "' ready.");
        } catch (SQLException e) {
            // #region agent log
            DebugLog.write("H1", "DatabaseHelper.initDB", "createDB SQLException",
                    "{\"sqlState\":\"" + e.getSQLState() + "\",\"msg\":\""
                            + (e.getMessage() == null ? "" : e.getMessage().replace("\"", "'")) + "\"}");
            // #endregion
            showError("initDB-createDB", e);
            return;
        }

        createTablesIfMissing();
        ensureSchemaUpgrades();
        createFunctionsTriggersAndViews();
        createStoredProcedures();
        tryImportSqlFile();
    }

    private static void createTablesIfMissing() {
        String donors =
            "CREATE TABLE IF NOT EXISTS donors (" +
            "  id INT AUTO_INCREMENT PRIMARY KEY," +
            "  name VARCHAR(100) NOT NULL," +
            "  age INT NOT NULL," +
            "  phone VARCHAR(20) NOT NULL," +
            "  email VARCHAR(100)," +
            "  blood_group VARCHAR(5) NOT NULL," +
            "  city VARCHAR(80)," +
            "  address VARCHAR(200)," +
            "  donation_count INT NOT NULL DEFAULT 0," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "  UNIQUE KEY uq_donor_name_phone (name, phone)," +
            "  INDEX idx_donor_blood_group (blood_group)," +
            "  INDEX idx_donor_age (age))";

        String requests =
            "CREATE TABLE IF NOT EXISTS blood_requests (" +
            "  id INT AUTO_INCREMENT PRIMARY KEY," +
            "  patient_name VARCHAR(100) NOT NULL," +
            "  blood_group VARCHAR(5) NOT NULL," +
            "  hospital VARCHAR(150)," +
            "  contact_phone VARCHAR(20)," +
            "  urgency VARCHAR(10) NOT NULL DEFAULT 'Normal'," +
            "  status VARCHAR(20) NOT NULL DEFAULT 'Pending'," +
            "  request_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "  INDEX idx_status (status)," +
            "  INDEX idx_blood_group (blood_group)," +
            "  INDEX idx_urgency_status (urgency, status))";

        String audit =
            "CREATE TABLE IF NOT EXISTS audit_log (" +
            "  id INT AUTO_INCREMENT PRIMARY KEY," +
            "  table_name VARCHAR(50) NOT NULL," +
            "  action_type VARCHAR(10) NOT NULL," +
            "  record_id INT," +
            "  old_values TEXT," +
            "  new_values TEXT," +
            "  logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  INDEX idx_audit_table (table_name))";

        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate(donors);
            s.executeUpdate(requests);
            s.executeUpdate(audit);
            System.out.println("[DB] Tables ready.");
        } catch (SQLException e) {
            showError("initDB-tables", e);
        }
    }

    /** Adds columns/indexes on existing DBs created before functions/triggers were added. */
    private static void ensureSchemaUpgrades() {
        String[] alters = {
            "ALTER TABLE donors ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP "
                    + "ON UPDATE CURRENT_TIMESTAMP",
            "ALTER TABLE blood_requests ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP "
                    + "ON UPDATE CURRENT_TIMESTAMP"
        };
        try (Connection c = connect(); Statement s = c.createStatement()) {
            for (String sql : alters) {
                try {
                    s.executeUpdate(sql);
                } catch (SQLException ignored) {
                    // column may already exist
                }
            }
        } catch (SQLException e) {
            showError("ensureSchemaUpgrades", e);
        }
    }

    /** SQL functions, triggers, and views (matches blood_bank.sql). */
    private static void createFunctionsTriggersAndViews() {
        String[] drops = {
            "DROP TRIGGER IF EXISTS trg_donors_before_insert",
            "DROP TRIGGER IF EXISTS trg_donors_before_update",
            "DROP TRIGGER IF EXISTS trg_donors_after_insert",
            "DROP TRIGGER IF EXISTS trg_donors_after_update",
            "DROP TRIGGER IF EXISTS trg_donors_after_delete",
            "DROP TRIGGER IF EXISTS trg_requests_before_insert",
            "DROP TRIGGER IF EXISTS trg_requests_before_update",
            "DROP TRIGGER IF EXISTS trg_requests_after_insert",
            "DROP TRIGGER IF EXISTS trg_requests_after_update",
            "DROP TRIGGER IF EXISTS trg_requests_after_delete",
            "DROP VIEW IF EXISTS vw_donor_summary_by_group",
            "DROP VIEW IF EXISTS vw_pending_requests_by_group",
            "DROP FUNCTION IF EXISTS fn_is_valid_blood_group",
            "DROP FUNCTION IF EXISTS fn_is_donor_eligible",
            "DROP FUNCTION IF EXISTS fn_count_donors_by_group",
            "DROP FUNCTION IF EXISTS fn_count_pending_requests"
        };

        String[] creates = {
            """
            CREATE FUNCTION fn_is_valid_blood_group(p_group VARCHAR(5))
            RETURNS TINYINT DETERMINISTIC NO SQL
            RETURN p_group IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')""",

            """
            CREATE FUNCTION fn_is_donor_eligible(p_age INT)
            RETURNS TINYINT DETERMINISTIC NO SQL
            RETURN (p_age >= 18 AND p_age <= 65)""",

            """
            CREATE FUNCTION fn_count_donors_by_group(p_blood_group VARCHAR(5))
            RETURNS INT READS SQL DATA DETERMINISTIC
            BEGIN
                DECLARE v_count INT DEFAULT 0;
                SELECT COUNT(*) INTO v_count FROM donors
                WHERE blood_group = p_blood_group AND fn_is_donor_eligible(age) = 1;
                RETURN v_count;
            END""",

            """
            CREATE FUNCTION fn_count_pending_requests(p_blood_group VARCHAR(5))
            RETURNS INT READS SQL DATA DETERMINISTIC
            BEGIN
                DECLARE v_count INT DEFAULT 0;
                SELECT COUNT(*) INTO v_count FROM blood_requests
                WHERE blood_group = p_blood_group AND status IN ('Pending', 'Matched');
                RETURN v_count;
            END""",

            """
            CREATE TRIGGER trg_donors_before_insert BEFORE INSERT ON donors
            FOR EACH ROW BEGIN
                SET NEW.name = TRIM(NEW.name);
                SET NEW.phone = TRIM(NEW.phone);
                IF fn_is_valid_blood_group(NEW.blood_group) = 0 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid blood group.';
                END IF;
                IF fn_is_donor_eligible(NEW.age) = 0 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Donor age must be 18-65.';
                END IF;
                IF NEW.donation_count < 0 THEN SET NEW.donation_count = 0; END IF;
            END""",

            """
            CREATE TRIGGER trg_donors_before_update BEFORE UPDATE ON donors
            FOR EACH ROW BEGIN
                IF fn_is_valid_blood_group(NEW.blood_group) = 0 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid blood group on update.';
                END IF;
                IF fn_is_donor_eligible(NEW.age) = 0 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Donor age must be 18-65 on update.';
                END IF;
                IF NEW.donation_count < 0 THEN SET NEW.donation_count = 0; END IF;
            END""",

            """
            CREATE TRIGGER trg_requests_before_insert BEFORE INSERT ON blood_requests
            FOR EACH ROW BEGIN
                IF fn_is_valid_blood_group(NEW.blood_group) = 0 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid blood group on request.';
                END IF;
                IF NEW.urgency NOT IN ('Normal', 'Urgent') THEN SET NEW.urgency = 'Normal'; END IF;
                IF NEW.status IS NULL OR NEW.status = '' THEN SET NEW.status = 'Pending'; END IF;
                IF NEW.status NOT IN ('Pending','Matched','Fulfilled','Cancelled') THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid request status.';
                END IF;
            END""",

            """
            CREATE TRIGGER trg_requests_before_update BEFORE UPDATE ON blood_requests
            FOR EACH ROW BEGIN
                IF NEW.status NOT IN ('Pending','Matched','Fulfilled','Cancelled') THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid status on update.';
                END IF;
                IF OLD.status = 'Fulfilled' AND NEW.blood_group <> OLD.blood_group THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot change blood group after fulfilled.';
                END IF;
            END""",

            """
            CREATE TRIGGER trg_donors_after_insert AFTER INSERT ON donors
            FOR EACH ROW BEGIN
                INSERT INTO audit_log (table_name, action_type, record_id, new_values)
                VALUES ('donors', 'INSERT', NEW.id,
                    CONCAT('name=', NEW.name, ';blood=', NEW.blood_group));
            END""",

            """
            CREATE TRIGGER trg_donors_after_update AFTER UPDATE ON donors
            FOR EACH ROW BEGIN
                INSERT INTO audit_log (table_name, action_type, record_id, old_values, new_values)
                VALUES ('donors', 'UPDATE', NEW.id,
                    CONCAT('donations=', OLD.donation_count),
                    CONCAT('donations=', NEW.donation_count));
            END""",

            """
            CREATE TRIGGER trg_donors_after_delete AFTER DELETE ON donors
            FOR EACH ROW BEGIN
                INSERT INTO audit_log (table_name, action_type, record_id, old_values)
                VALUES ('donors', 'DELETE', OLD.id, CONCAT('name=', OLD.name));
            END""",

            """
            CREATE TRIGGER trg_requests_after_insert AFTER INSERT ON blood_requests
            FOR EACH ROW BEGIN
                INSERT INTO audit_log (table_name, action_type, record_id, new_values)
                VALUES ('blood_requests', 'INSERT', NEW.id,
                    CONCAT('patient=', NEW.patient_name, ';status=', NEW.status));
            END""",

            """
            CREATE TRIGGER trg_requests_after_update AFTER UPDATE ON blood_requests
            FOR EACH ROW BEGIN
                INSERT INTO audit_log (table_name, action_type, record_id, old_values, new_values)
                VALUES ('blood_requests', 'UPDATE', NEW.id,
                    CONCAT('status=', OLD.status), CONCAT('status=', NEW.status));
            END""",

            """
            CREATE TRIGGER trg_requests_after_delete AFTER DELETE ON blood_requests
            FOR EACH ROW BEGIN
                INSERT INTO audit_log (table_name, action_type, record_id, old_values)
                VALUES ('blood_requests', 'DELETE', OLD.id, CONCAT('patient=', OLD.patient_name));
            END""",

            """
            CREATE VIEW vw_donor_summary_by_group AS
            SELECT blood_group, COUNT(*) AS total_donors,
                SUM(CASE WHEN fn_is_donor_eligible(age) = 1 THEN 1 ELSE 0 END) AS eligible_donors,
                SUM(donation_count) AS total_donations
            FROM donors GROUP BY blood_group""",

            """
            CREATE VIEW vw_pending_requests_by_group AS
            SELECT blood_group, COUNT(*) AS pending_count,
                SUM(CASE WHEN urgency = 'Urgent' THEN 1 ELSE 0 END) AS urgent_count
            FROM blood_requests
            WHERE status IN ('Pending', 'Matched') GROUP BY blood_group"""
        };

        try (Connection c = connect(); Statement s = c.createStatement()) {
            for (String drop : drops) {
                s.execute(drop);
            }
            for (String create : creates) {
                s.execute(create);
            }
            System.out.println("[DB] Functions, triggers, and views ready.");
        } catch (SQLException e) {
            showError("initDB-functions-triggers", e);
        }
    }

    /** Creates / updates all stored procedures (matches blood_bank.sql). */
    private static void createStoredProcedures() {
        String[] drops = {
            "DROP PROCEDURE IF EXISTS sp_add_donor",
            "DROP PROCEDURE IF EXISTS sp_get_all_donors",
            "DROP PROCEDURE IF EXISTS sp_delete_donor",
            "DROP PROCEDURE IF EXISTS sp_increment_donation",
            "DROP PROCEDURE IF EXISTS sp_add_blood_request",
            "DROP PROCEDURE IF EXISTS sp_get_all_requests",
            "DROP PROCEDURE IF EXISTS sp_update_request_status",
            "DROP PROCEDURE IF EXISTS sp_delete_request",
            "DROP PROCEDURE IF EXISTS sp_fulfill_request_with_donation",
            "DROP PROCEDURE IF EXISTS sp_get_dashboard_stats",
            "DROP PROCEDURE IF EXISTS sp_find_match_summary"
        };

        String[] creates = {
            """
            CREATE PROCEDURE sp_add_donor(
                IN p_name VARCHAR(100), IN p_age INT, IN p_phone VARCHAR(20),
                IN p_email VARCHAR(100), IN p_blood_group VARCHAR(5),
                IN p_city VARCHAR(80), IN p_address VARCHAR(200),
                IN p_donation_count INT)
            BEGIN
                INSERT INTO donors (name, age, phone, email, blood_group, city, address, donation_count)
                VALUES (p_name, p_age, p_phone, p_email, p_blood_group, p_city, p_address, p_donation_count);
            END""",

            """
            CREATE PROCEDURE sp_get_all_donors(IN p_blood_group VARCHAR(5))
            BEGIN
                IF p_blood_group IS NULL OR p_blood_group = '' THEN
                    SELECT * FROM donors ORDER BY name;
                ELSE
                    SELECT * FROM donors WHERE blood_group = p_blood_group ORDER BY name;
                END IF;
            END""",

            """
            CREATE PROCEDURE sp_delete_donor(
                IN p_name VARCHAR(100), IN p_phone VARCHAR(20), OUT p_rows INT)
            BEGIN
                DELETE FROM donors WHERE name = p_name AND phone = p_phone;
                SET p_rows = ROW_COUNT();
            END""",

            """
            CREATE PROCEDURE sp_increment_donation(
                IN p_name VARCHAR(100), IN p_phone VARCHAR(20), OUT p_rows INT)
            BEGIN
                UPDATE donors SET donation_count = donation_count + 1
                WHERE name = p_name AND phone = p_phone;
                SET p_rows = ROW_COUNT();
            END""",

            """
            CREATE PROCEDURE sp_add_blood_request(
                IN p_patient_name VARCHAR(100), IN p_blood_group VARCHAR(5),
                IN p_hospital VARCHAR(150), IN p_contact_phone VARCHAR(20),
                IN p_urgency VARCHAR(10), IN p_status VARCHAR(20))
            BEGIN
                INSERT INTO blood_requests (patient_name, blood_group, hospital, contact_phone, urgency, status)
                VALUES (p_patient_name, p_blood_group, p_hospital, p_contact_phone, p_urgency, p_status);
            END""",

            """
            CREATE PROCEDURE sp_get_all_requests(IN p_status VARCHAR(20))
            BEGIN
                IF p_status IS NULL OR p_status = '' THEN
                    SELECT * FROM blood_requests
                    ORDER BY FIELD(urgency, 'Urgent', 'Normal'), request_date DESC;
                ELSE
                    SELECT * FROM blood_requests WHERE status = p_status
                    ORDER BY FIELD(urgency, 'Urgent', 'Normal'), request_date DESC;
                END IF;
            END""",

            """
            CREATE PROCEDURE sp_update_request_status(
                IN p_id INT, IN p_status VARCHAR(20), OUT p_rows INT)
            BEGIN
                UPDATE blood_requests SET status = p_status WHERE id = p_id;
                SET p_rows = ROW_COUNT();
            END""",

            """
            CREATE PROCEDURE sp_delete_request(IN p_id INT, OUT p_rows INT)
            BEGIN
                DELETE FROM blood_requests WHERE id = p_id;
                SET p_rows = ROW_COUNT();
            END""",

            """
            CREATE PROCEDURE sp_fulfill_request_with_donation(
                IN p_request_id INT, IN p_donor_name VARCHAR(100),
                IN p_donor_phone VARCHAR(20), OUT p_success TINYINT)
            proc_label: BEGIN
                DECLARE v_req_rows INT DEFAULT 0;
                DECLARE v_don_rows INT DEFAULT 0;
                DECLARE EXIT HANDLER FOR SQLEXCEPTION
                BEGIN
                    ROLLBACK;
                    SET p_success = 0;
                END;
                START TRANSACTION;
                UPDATE blood_requests SET status = 'Fulfilled' WHERE id = p_request_id;
                SET v_req_rows = ROW_COUNT();
                UPDATE donors SET donation_count = donation_count + 1
                WHERE name = p_donor_name AND phone = p_donor_phone;
                SET v_don_rows = ROW_COUNT();
                IF v_req_rows > 0 AND v_don_rows > 0 THEN
                    COMMIT;
                    SET p_success = 1;
                ELSE
                    ROLLBACK;
                    SET p_success = 0;
                END IF;
            END""",

            """
            CREATE PROCEDURE sp_get_dashboard_stats()
            BEGIN
                SELECT
                    (SELECT COUNT(*) FROM donors) AS total_donors,
                    (SELECT COUNT(*) FROM donors WHERE fn_is_donor_eligible(age) = 1) AS eligible_donors,
                    (SELECT COALESCE(SUM(donation_count), 0) FROM donors) AS total_donations,
                    (SELECT COUNT(*) FROM blood_requests) AS total_requests,
                    (SELECT COUNT(*) FROM blood_requests WHERE status = 'Pending') AS pending_requests,
                    (SELECT COUNT(*) FROM blood_requests WHERE status = 'Fulfilled') AS fulfilled_requests;
            END""",

            """
            CREATE PROCEDURE sp_find_match_summary(IN p_blood_group VARCHAR(5))
            BEGIN
                SELECT p_blood_group AS blood_group_needed,
                    fn_count_donors_by_group(p_blood_group) AS matching_eligible_donors,
                    fn_count_pending_requests(p_blood_group) AS pending_requests_same_group;
            END"""
        };

        try (Connection c = connect(); Statement s = c.createStatement()) {
            for (String drop : drops) {
                s.execute(drop);
            }
            for (String create : creates) {
                s.execute(create);
            }
            System.out.println("[DB] Stored procedures ready.");
        } catch (SQLException e) {
            showError("initDB-procedures", e);
        }
    }

    /** Optional: run table DDL from blood_bank.sql if the file is present. */
    private static void tryImportSqlFile() {
        Path path = Paths.get(SQL_FILE);
        if (!Files.isRegularFile(path)) {
            System.out.println("[DB] Tip: place " + SQL_FILE + " here for manual phpMyAdmin import.");
            return;
        }
        System.out.println("[DB] Found " + SQL_FILE + " – use phpMyAdmin to import sample data if needed.");
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DONOR METHODS  (stored procedures)
    // ═════════════════════════════════════════════════════════════════════

    public static boolean addDonor(Donor donor) {
        String call = "{CALL sp_add_donor(?,?,?,?,?,?,?,?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            cs.setString(1, donor.getName());
            cs.setInt(2, donor.getAge());
            cs.setString(3, donor.getPhone());
            cs.setString(4, donor.getEmail());
            cs.setString(5, donor.getBloodGroup());
            cs.setString(6, donor.getContactInfo().getCity());
            cs.setString(7, donor.getContactInfo().getAddress());
            cs.setInt(8, donor.getDonationCount());
            cs.execute();
            return true;
        } catch (SQLException e) {
            showError("addDonor", e);
            return false;
        }
    }

    public static List<Donor> getAllDonors(String bloodGroupFilter) {
        List<Donor> list = new ArrayList<>();
        String call = "{CALL sp_get_all_donors(?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            if (bloodGroupFilter == null || bloodGroupFilter.isBlank()) {
                cs.setNull(1, Types.VARCHAR);
            } else {
                cs.setString(1, bloodGroupFilter);
            }
            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    list.add(mapDonor(rs));
                }
            }
        } catch (SQLException e) {
            showError("getAllDonors", e);
        }
        return list;
    }

    public static boolean deleteDonor(String name, String phone) {
        String call = "{CALL sp_delete_donor(?,?,?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            cs.setString(1, name);
            cs.setString(2, phone);
            cs.registerOutParameter(3, Types.INTEGER);
            cs.execute();
            return cs.getInt(3) > 0;
        } catch (SQLException e) {
            showError("deleteDonor", e);
            return false;
        }
    }

    public static boolean incrementDonationCount(String name, String phone) {
        String call = "{CALL sp_increment_donation(?,?,?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            cs.setString(1, name);
            cs.setString(2, phone);
            cs.registerOutParameter(3, Types.INTEGER);
            cs.execute();
            return cs.getInt(3) > 0;
        } catch (SQLException e) {
            showError("incrementDonationCount", e);
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  BLOOD REQUEST METHODS  (stored procedures)
    // ═════════════════════════════════════════════════════════════════════

    public static boolean addRequest(BloodRequest req) {
        String call = "{CALL sp_add_blood_request(?,?,?,?,?,?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            cs.setString(1, req.getPatientName());
            cs.setString(2, req.getBloodGroupNeeded());
            cs.setString(3, req.getHospital());
            cs.setString(4, req.getContactPhone());
            cs.setString(5, req.getUrgency());
            cs.setString(6, req.getStatus());
            cs.execute();
            return true;
        } catch (SQLException e) {
            showError("addRequest", e);
            return false;
        }
    }

    /** Returns the highest request id (used for PDF receipt after insert). */
    public static int getLatestRequestId() {
        try (Connection c = connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(id), 0) FROM blood_requests")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            showError("getLatestRequestId", e);
        }
        return 0;
    }

    public static List<BloodRequest> getAllRequests(String statusFilter) {
        List<BloodRequest> list = new ArrayList<>();
        String call = "{CALL sp_get_all_requests(?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            if (statusFilter == null || statusFilter.isBlank()) {
                cs.setNull(1, Types.VARCHAR);
            } else {
                cs.setString(1, statusFilter);
            }
            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRequest(rs));
                }
            }
        } catch (SQLException e) {
            showError("getAllRequests", e);
        }
        return list;
    }

    public static boolean updateRequestStatus(int id, String newStatus) {
        String call = "{CALL sp_update_request_status(?,?,?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            cs.setInt(1, id);
            cs.setString(2, newStatus);
            cs.registerOutParameter(3, Types.INTEGER);
            cs.execute();
            return cs.getInt(3) > 0;
        } catch (SQLException e) {
            showError("updateRequestStatus", e);
            return false;
        }
    }

    public static boolean deleteRequest(int id) {
        String call = "{CALL sp_delete_request(?,?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            cs.setInt(1, id);
            cs.registerOutParameter(2, Types.INTEGER);
            cs.execute();
            return cs.getInt(2) > 0;
        } catch (SQLException e) {
            showError("deleteRequest", e);
            return false;
        }
    }

    /**
     * Transactional fulfill: marks request Fulfilled and increments donor count
     * in one MySQL transaction (sp_fulfill_request_with_donation).
     */
    public static boolean fulfillRequestWithDonation(int requestId, String donorName, String donorPhone) {
        String call = "{CALL sp_fulfill_request_with_donation(?,?,?,?)}";
        try (Connection c = connect();
             CallableStatement cs = c.prepareCall(call)) {
            cs.setInt(1, requestId);
            cs.setString(2, donorName);
            cs.setString(3, donorPhone);
            cs.registerOutParameter(4, Types.TINYINT);
            cs.execute();
            return cs.getInt(4) == 1;
        } catch (SQLException e) {
            showError("fulfillRequestWithDonation", e);
            return false;
        }
    }

    public static List<Donor> findMatchingDonors(String bloodGroup) {
        return getAllDonors(bloodGroup);
    }

    private static Donor mapDonor(ResultSet rs) throws SQLException {
        Donor d = new Donor(
                rs.getString("name"), rs.getInt("age"),
                rs.getString("phone"), rs.getString("email"),
                rs.getString("blood_group"),
                rs.getString("city"), rs.getString("address"));
        d.setDonationCount(rs.getInt("donation_count"));
        return d;
    }

    private static BloodRequest mapRequest(ResultSet rs) throws SQLException {
        BloodRequest r = new BloodRequest(
                rs.getString("patient_name"),
                rs.getString("blood_group"),
                rs.getString("hospital"),
                rs.getString("contact_phone"),
                rs.getString("urgency"));
        r.setId(rs.getInt("id"));
        r.setStatus(rs.getString("status"));
        return r;
    }

    private static void showError(String method, SQLException e) {
        // #region agent log
        DebugLog.write("H5", "DatabaseHelper.showError", method,
                "{\"msg\":\"" + (e.getMessage() == null ? "" : e.getMessage().replace("\"", "'")) + "\"}");
        // #endregion
        System.err.println("[DB ERROR in " + method + "] " + e.getMessage());
    }
}
