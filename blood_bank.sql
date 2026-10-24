-- ============================================================
--  blood_bank.sql  –  Blood Bank Management System (MySQL)
--  Run in phpMyAdmin / MySQL CLI, or let the Java app load it
--  on first start via DatabaseHelper.initDB().
--
--  Includes:
--    • Database + tables (indexes for faster lookups)
--    • SQL functions (validation, counts, eligibility)
--    • Triggers (data integrity, audit log, auto timestamps)
--    • Views (pre-aggregated stats)
--    • Stored procedures (CRUD)
--    • Transactions inside procedures
-- ============================================================

CREATE DATABASE IF NOT EXISTS blood_bank
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE blood_bank;

-- ─────────────────────────────────────────────────────────────
--  TABLES
-- ─────────────────────────────────────────────────────────────

DROP TRIGGER IF EXISTS trg_donors_before_insert;
DROP TRIGGER IF EXISTS trg_donors_before_update;
DROP TRIGGER IF EXISTS trg_donors_after_insert;
DROP TRIGGER IF EXISTS trg_donors_after_update;
DROP TRIGGER IF EXISTS trg_donors_after_delete;
DROP TRIGGER IF EXISTS trg_requests_before_insert;
DROP TRIGGER IF EXISTS trg_requests_before_update;
DROP TRIGGER IF EXISTS trg_requests_after_insert;
DROP TRIGGER IF EXISTS trg_requests_after_update;
DROP TRIGGER IF EXISTS trg_requests_after_delete;

DROP VIEW IF EXISTS vw_donor_summary_by_group;
DROP VIEW IF EXISTS vw_pending_requests_by_group;

DROP FUNCTION IF EXISTS fn_is_valid_blood_group;
DROP FUNCTION IF EXISTS fn_is_donor_eligible;
DROP FUNCTION IF EXISTS fn_count_donors_by_group;
DROP FUNCTION IF EXISTS fn_count_pending_requests;

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS blood_requests;
DROP TABLE IF EXISTS donors;

CREATE TABLE donors (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    age             INT NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    email           VARCHAR(100),
    blood_group     VARCHAR(5) NOT NULL,
    city            VARCHAR(80),
    address         VARCHAR(200),
    donation_count  INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_donor_name_phone (name, phone),
    INDEX idx_donor_blood_group (blood_group),
    INDEX idx_donor_age (age),
    INDEX idx_donor_city (city)
);

CREATE TABLE blood_requests (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    patient_name    VARCHAR(100) NOT NULL,
    blood_group     VARCHAR(5) NOT NULL,
    hospital        VARCHAR(150),
    contact_phone   VARCHAR(20),
    urgency         VARCHAR(10) NOT NULL DEFAULT 'Normal',
    status          VARCHAR(20) NOT NULL DEFAULT 'Pending',
    request_date    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_blood_group (blood_group),
    INDEX idx_urgency_status (urgency, status),
    INDEX idx_request_date (request_date)
);

-- Audit trail (populated by triggers)
CREATE TABLE audit_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    table_name  VARCHAR(50) NOT NULL,
    action_type VARCHAR(10) NOT NULL,
    record_id   INT,
    old_values  TEXT,
    new_values  TEXT,
    logged_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_table (table_name),
    INDEX idx_audit_time (logged_at)
);

-- ─────────────────────────────────────────────────────────────
--  SAMPLE DATA (optional – comment out if not wanted)
-- ─────────────────────────────────────────────────────────────

INSERT INTO donors (name, age, phone, email, blood_group, city, address, donation_count)
VALUES
    ('Ali Khan',     28, '03001234567', 'ali@email.com',  'O+',  'Lahore', 'Gulberg III', 2),
    ('Sara Ahmed',   34, '03009876543', 'sara@email.com', 'A+',  'Karachi','Clifton',     1),
    ('Hassan Raza',  22, '03111222333', NULL,             'B-',  'Islamabad','F-8',       0);

INSERT INTO blood_requests (patient_name, blood_group, hospital, contact_phone, urgency, status)
VALUES
    ('Patient A', 'O+', 'City Hospital',   '03211111111', 'Urgent', 'Pending'),
    ('Patient B', 'A+', 'General Hospital','03222222222', 'Normal', 'Pending');

-- ─────────────────────────────────────────────────────────────
--  SQL FUNCTIONS (reusable validation & counts)
-- ─────────────────────────────────────────────────────────────

DELIMITER $$

CREATE FUNCTION fn_is_valid_blood_group(p_group VARCHAR(5))
RETURNS TINYINT
DETERMINISTIC
NO SQL
BEGIN
    RETURN p_group IN ('A+','A-','B+','B-','AB+','AB-','O+','O-');
END$$

CREATE FUNCTION fn_is_donor_eligible(p_age INT)
RETURNS TINYINT
DETERMINISTIC
NO SQL
BEGIN
    RETURN (p_age >= 18 AND p_age <= 65);
END$$

CREATE FUNCTION fn_count_donors_by_group(p_blood_group VARCHAR(5))
RETURNS INT
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_count INT DEFAULT 0;
    SELECT COUNT(*) INTO v_count
    FROM donors
    WHERE blood_group = p_blood_group
      AND fn_is_donor_eligible(age) = 1;
    RETURN v_count;
END$$

CREATE FUNCTION fn_count_pending_requests(p_blood_group VARCHAR(5))
RETURNS INT
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_count INT DEFAULT 0;
    SELECT COUNT(*) INTO v_count
    FROM blood_requests
    WHERE blood_group = p_blood_group
      AND status IN ('Pending', 'Matched');
    RETURN v_count;
END$$

-- ─────────────────────────────────────────────────────────────
--  TRIGGERS (integrity, normalization, audit)
-- ─────────────────────────────────────────────────────────────

CREATE TRIGGER trg_donors_before_insert
BEFORE INSERT ON donors
FOR EACH ROW
BEGIN
    SET NEW.name  = TRIM(NEW.name);
    SET NEW.phone = TRIM(NEW.phone);
    IF NEW.email IS NOT NULL THEN
        SET NEW.email = TRIM(NEW.email);
    END IF;
    IF fn_is_valid_blood_group(NEW.blood_group) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid blood group. Use A+, A-, B+, B-, AB+, AB-, O+, O-.';
    END IF;
    IF fn_is_donor_eligible(NEW.age) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Donor age must be between 18 and 65.';
    END IF;
    IF NEW.donation_count < 0 THEN
        SET NEW.donation_count = 0;
    END IF;
END$$

CREATE TRIGGER trg_donors_before_update
BEFORE UPDATE ON donors
FOR EACH ROW
BEGIN
    SET NEW.name  = TRIM(NEW.name);
    SET NEW.phone = TRIM(NEW.phone);
    IF NEW.email IS NOT NULL THEN
        SET NEW.email = TRIM(NEW.email);
    END IF;
    IF fn_is_valid_blood_group(NEW.blood_group) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid blood group on update.';
    END IF;
    IF fn_is_donor_eligible(NEW.age) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Donor age must be between 18 and 65 on update.';
    END IF;
    IF NEW.donation_count < 0 THEN
        SET NEW.donation_count = 0;
    END IF;
END$$

CREATE TRIGGER trg_requests_before_insert
BEFORE INSERT ON blood_requests
FOR EACH ROW
BEGIN
    SET NEW.patient_name = TRIM(NEW.patient_name);
    IF NEW.hospital IS NOT NULL THEN
        SET NEW.hospital = TRIM(NEW.hospital);
    END IF;
    IF fn_is_valid_blood_group(NEW.blood_group) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid blood group on blood request.';
    END IF;
    IF NEW.urgency NOT IN ('Normal', 'Urgent') THEN
        SET NEW.urgency = 'Normal';
    END IF;
    IF NEW.status IS NULL OR NEW.status = '' THEN
        SET NEW.status = 'Pending';
    END IF;
    IF NEW.status NOT IN ('Pending', 'Matched', 'Fulfilled', 'Cancelled') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid request status.';
    END IF;
END$$

CREATE TRIGGER trg_requests_before_update
BEFORE UPDATE ON blood_requests
FOR EACH ROW
BEGIN
    IF fn_is_valid_blood_group(NEW.blood_group) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid blood group on request update.';
    END IF;
    IF NEW.urgency NOT IN ('Normal', 'Urgent') THEN
        SET NEW.urgency = OLD.urgency;
    END IF;
    IF NEW.status NOT IN ('Pending', 'Matched', 'Fulfilled', 'Cancelled') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid request status on update.';
    END IF;
    -- Prevent changing blood group after request is fulfilled
    IF OLD.status = 'Fulfilled' AND NEW.blood_group <> OLD.blood_group THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot change blood group of a fulfilled request.';
    END IF;
END$$

CREATE TRIGGER trg_donors_after_insert
AFTER INSERT ON donors
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (table_name, action_type, record_id, new_values)
    VALUES ('donors', 'INSERT', NEW.id,
            CONCAT('name=', NEW.name, ';blood=', NEW.blood_group, ';phone=', NEW.phone));
END$$

CREATE TRIGGER trg_donors_after_update
AFTER UPDATE ON donors
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (table_name, action_type, record_id, old_values, new_values)
    VALUES ('donors', 'UPDATE', NEW.id,
            CONCAT('donations=', OLD.donation_count, ';blood=', OLD.blood_group),
            CONCAT('donations=', NEW.donation_count, ';blood=', NEW.blood_group));
END$$

CREATE TRIGGER trg_donors_after_delete
AFTER DELETE ON donors
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (table_name, action_type, record_id, old_values)
    VALUES ('donors', 'DELETE', OLD.id,
            CONCAT('name=', OLD.name, ';phone=', OLD.phone));
END$$

CREATE TRIGGER trg_requests_after_insert
AFTER INSERT ON blood_requests
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (table_name, action_type, record_id, new_values)
    VALUES ('blood_requests', 'INSERT', NEW.id,
            CONCAT('patient=', NEW.patient_name, ';blood=', NEW.blood_group,
                   ';urgency=', NEW.urgency, ';status=', NEW.status));
END$$

CREATE TRIGGER trg_requests_after_update
AFTER UPDATE ON blood_requests
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (table_name, action_type, record_id, old_values, new_values)
    VALUES ('blood_requests', 'UPDATE', NEW.id,
            CONCAT('status=', OLD.status, ';urgency=', OLD.urgency),
            CONCAT('status=', NEW.status, ';urgency=', NEW.urgency));
END$$

CREATE TRIGGER trg_requests_after_delete
AFTER DELETE ON blood_requests
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (table_name, action_type, record_id, old_values)
    VALUES ('blood_requests', 'DELETE', OLD.id,
            CONCAT('patient=', OLD.patient_name, ';blood=', OLD.blood_group));
END$$

-- ─────────────────────────────────────────────────────────────
--  VIEWS (optimized read paths for reports / matching)
-- ─────────────────────────────────────────────────────────────

CREATE VIEW vw_donor_summary_by_group AS
SELECT
    blood_group,
    COUNT(*) AS total_donors,
    SUM(CASE WHEN fn_is_donor_eligible(age) = 1 THEN 1 ELSE 0 END) AS eligible_donors,
    SUM(donation_count) AS total_donations,
    MAX(donation_count) AS max_donations_one_donor
FROM donors
GROUP BY blood_group$$

CREATE VIEW vw_pending_requests_by_group AS
SELECT
    blood_group,
    COUNT(*) AS pending_count,
    SUM(CASE WHEN urgency = 'Urgent' THEN 1 ELSE 0 END) AS urgent_count
FROM blood_requests
WHERE status IN ('Pending', 'Matched')
GROUP BY blood_group$$

DELIMITER ;

-- ─────────────────────────────────────────────────────────────
--  DROP EXISTING PROCEDURES (safe re-run)
-- ─────────────────────────────────────────────────────────────

DROP PROCEDURE IF EXISTS sp_add_donor;
DROP PROCEDURE IF EXISTS sp_get_all_donors;
DROP PROCEDURE IF EXISTS sp_delete_donor;
DROP PROCEDURE IF EXISTS sp_increment_donation;
DROP PROCEDURE IF EXISTS sp_add_blood_request;
DROP PROCEDURE IF EXISTS sp_get_all_requests;
DROP PROCEDURE IF EXISTS sp_update_request_status;
DROP PROCEDURE IF EXISTS sp_delete_request;
DROP PROCEDURE IF EXISTS sp_fulfill_request_with_donation;
DROP PROCEDURE IF EXISTS sp_get_dashboard_stats;
DROP PROCEDURE IF EXISTS sp_find_match_summary;

DELIMITER $$

-- ─────────────────────────────────────────────────────────────
--  DONOR STORED PROCEDURES
-- ─────────────────────────────────────────────────────────────

CREATE PROCEDURE sp_add_donor(
    IN p_name           VARCHAR(100),
    IN p_age            INT,
    IN p_phone          VARCHAR(20),
    IN p_email          VARCHAR(100),
    IN p_blood_group    VARCHAR(5),
    IN p_city           VARCHAR(80),
    IN p_address        VARCHAR(200),
    IN p_donation_count INT
)
BEGIN
    INSERT INTO donors (name, age, phone, email, blood_group, city, address, donation_count)
    VALUES (p_name, p_age, p_phone, p_email, p_blood_group, p_city, p_address, p_donation_count);
END$$

CREATE PROCEDURE sp_get_all_donors(
    IN p_blood_group VARCHAR(5)
)
BEGIN
    IF p_blood_group IS NULL OR p_blood_group = '' THEN
        SELECT * FROM donors ORDER BY name;
    ELSE
        SELECT * FROM donors WHERE blood_group = p_blood_group ORDER BY name;
    END IF;
END$$

CREATE PROCEDURE sp_delete_donor(
    IN p_name  VARCHAR(100),
    IN p_phone VARCHAR(20),
    OUT p_rows INT
)
BEGIN
    DELETE FROM donors WHERE name = p_name AND phone = p_phone;
    SET p_rows = ROW_COUNT();
END$$

CREATE PROCEDURE sp_increment_donation(
    IN p_name  VARCHAR(100),
    IN p_phone VARCHAR(20),
    OUT p_rows INT
)
BEGIN
    UPDATE donors
    SET donation_count = donation_count + 1
    WHERE name = p_name AND phone = p_phone;
    SET p_rows = ROW_COUNT();
END$$

-- ─────────────────────────────────────────────────────────────
--  BLOOD REQUEST STORED PROCEDURES
-- ─────────────────────────────────────────────────────────────

CREATE PROCEDURE sp_add_blood_request(
    IN p_patient_name VARCHAR(100),
    IN p_blood_group  VARCHAR(5),
    IN p_hospital     VARCHAR(150),
    IN p_contact_phone VARCHAR(20),
    IN p_urgency      VARCHAR(10),
    IN p_status       VARCHAR(20)
)
BEGIN
    INSERT INTO blood_requests (patient_name, blood_group, hospital, contact_phone, urgency, status)
    VALUES (p_patient_name, p_blood_group, p_hospital, p_contact_phone, p_urgency, p_status);
END$$

CREATE PROCEDURE sp_get_all_requests(
    IN p_status VARCHAR(20)
)
BEGIN
    IF p_status IS NULL OR p_status = '' THEN
        SELECT * FROM blood_requests
        ORDER BY FIELD(urgency, 'Urgent', 'Normal'), request_date DESC;
    ELSE
        SELECT * FROM blood_requests
        WHERE status = p_status
        ORDER BY FIELD(urgency, 'Urgent', 'Normal'), request_date DESC;
    END IF;
END$$

CREATE PROCEDURE sp_update_request_status(
    IN p_id     INT,
    IN p_status VARCHAR(20),
    OUT p_rows  INT
)
BEGIN
    UPDATE blood_requests SET status = p_status WHERE id = p_id;
    SET p_rows = ROW_COUNT();
END$$

CREATE PROCEDURE sp_delete_request(
    IN p_id    INT,
    OUT p_rows INT
)
BEGIN
    DELETE FROM blood_requests WHERE id = p_id;
    SET p_rows = ROW_COUNT();
END$$

-- ─────────────────────────────────────────────────────────────
--  TRANSACTION: Fulfill request + record donor donation
--  Rolls back both changes if either step fails.
-- ─────────────────────────────────────────────────────────────

CREATE PROCEDURE sp_fulfill_request_with_donation(
    IN  p_request_id   INT,
    IN  p_donor_name   VARCHAR(100),
    IN  p_donor_phone  VARCHAR(20),
    OUT p_success      TINYINT
)
proc_label: BEGIN
    DECLARE v_req_rows INT DEFAULT 0;
    DECLARE v_don_rows INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_success = 0;
    END;

    START TRANSACTION;

    UPDATE blood_requests
    SET status = 'Fulfilled'
    WHERE id = p_request_id;
    SET v_req_rows = ROW_COUNT();

    UPDATE donors
    SET donation_count = donation_count + 1
    WHERE name = p_donor_name AND phone = p_donor_phone;
    SET v_don_rows = ROW_COUNT();

    IF v_req_rows > 0 AND v_don_rows > 0 THEN
        COMMIT;
        SET p_success = 1;
    ELSE
        ROLLBACK;
        SET p_success = 0;
    END IF;
END$$

-- Uses functions + views for quick dashboard / matching overview
CREATE PROCEDURE sp_get_dashboard_stats()
BEGIN
    SELECT
        (SELECT COUNT(*) FROM donors) AS total_donors,
        (SELECT COUNT(*) FROM donors WHERE fn_is_donor_eligible(age) = 1) AS eligible_donors,
        (SELECT SUM(donation_count) FROM donors) AS total_donations,
        (SELECT COUNT(*) FROM blood_requests) AS total_requests,
        (SELECT COUNT(*) FROM blood_requests WHERE status = 'Pending') AS pending_requests,
        (SELECT COUNT(*) FROM blood_requests WHERE status = 'Fulfilled') AS fulfilled_requests;
END$$

CREATE PROCEDURE sp_find_match_summary(IN p_blood_group VARCHAR(5))
BEGIN
    SELECT
        p_blood_group AS blood_group_needed,
        fn_count_donors_by_group(p_blood_group) AS matching_eligible_donors,
        fn_count_pending_requests(p_blood_group) AS pending_requests_same_group;
END$$

DELIMITER ;
