-- =============================================================================
--  Core stored procedures: logs, audit, login, sessions, OTP, page rights.
--  Convention (same as Health 360): every parameter starts with p_,
--  results come back as SELECT result sets, writes return the new id.
-- =============================================================================

-- ------------------------------------------------------------------ logs

CREATE PROCEDURE sp_store_error_log(
  IN p_class_name VARCHAR(100), IN p_method_name VARCHAR(100), IN p_message VARCHAR(500),
  IN p_error_description TEXT, IN p_stack_trace TEXT, IN p_user_id BIGINT)
BEGIN
  INSERT INTO error_logs (class_name, method_name, message, error_description, stack_trace, user_id, inserted_by)
  VALUES (p_class_name, p_method_name, LEFT(p_message, 500), p_error_description, p_stack_trace, p_user_id, p_user_id);
END;

CREATE PROCEDURE sp_log_request(
  IN p_api_url VARCHAR(200), IN p_request_json TEXT, IN p_status_code INT, IN p_duration_ms INT,
  IN p_user_id BIGINT, IN p_business_id BIGINT, IN p_ip_address VARCHAR(45))
BEGIN
  INSERT INTO api_request_logs (api_url, request_json, status_code, duration_ms, user_id, business_id, ip_address, inserted_by)
  VALUES (p_api_url, p_request_json, p_status_code, p_duration_ms, p_user_id, p_business_id, p_ip_address, p_user_id);
END;

-- ------------------------------------------------------------------ audit

CREATE PROCEDURE sp_audit_add(
  IN p_business_id BIGINT, IN p_user_id BIGINT, IN p_action_code VARCHAR(50), IN p_entity_name VARCHAR(50),
  IN p_entity_id BIGINT, IN p_new_value TEXT, IN p_reason VARCHAR(255), IN p_ip_address VARCHAR(45))
BEGIN
  INSERT INTO audit_logs (business_id, user_id, action_code, entity_name, entity_id, new_value, reason, ip_address, inserted_by)
  VALUES (p_business_id, p_user_id, p_action_code, p_entity_name, p_entity_id, p_new_value, p_reason, p_ip_address, p_user_id);
END;

-- p_business_id NULL = platform admin: every business
CREATE PROCEDURE sp_audit_get_all(IN p_business_id BIGINT, IN p_limit INT)
BEGIN
  SELECT a.audit_log_id, a.business_id, COALESCE(b.business_name, 'Platform') AS business_name,
         COALESCE(u.full_name, 'System') AS user_name, COALESCE(r.role_code, '') AS role_code,
         a.action_code, a.entity_name, COALESCE(a.new_value, '') AS details, COALESCE(a.reason, '') AS reason,
         a.action_date
  FROM audit_logs a
  LEFT JOIN businesses b ON b.business_id = a.business_id
  LEFT JOIN users u ON u.user_id = a.user_id
  LEFT JOIN roles r ON r.role_id = u.role_id
  WHERE a.is_active = 1 AND (p_business_id IS NULL OR a.business_id = p_business_id)
  ORDER BY a.audit_log_id DESC
  LIMIT p_limit;
END;

-- ------------------------------------------------------------------ login

-- Find an active user by mobile number or email, with what login needs to decide.
CREATE PROCEDURE sp_auth_get_user_for_login(IN p_login_id VARCHAR(150))
BEGIN
  SELECT u.user_id, u.business_id, r.role_code, u.full_name, u.mobile_no, u.email, u.password_hash,
         u.preferred_language, u.is_active, u.failed_login_count, u.locked_until, u.must_change_password,
         b.status AS business_status
  FROM users u
  JOIN roles r ON r.role_id = u.role_id
  LEFT JOIN businesses b ON b.business_id = u.business_id
  WHERE u.mobile_no = p_login_id OR (u.email IS NOT NULL AND u.email = p_login_id)
  LIMIT 1;
END;

-- Success: reset tries + last login. Failure: +1 try, lock 15 min after 5. Always writes login_history.
CREATE PROCEDURE sp_auth_record_login(
  IN p_user_id BIGINT, IN p_login_id VARCHAR(150), IN p_is_success TINYINT, IN p_failure_reason VARCHAR(100),
  IN p_ip_address VARCHAR(45), IN p_device_name VARCHAR(150))
BEGIN
  IF p_user_id IS NOT NULL THEN
    IF p_is_success = 1 THEN
      UPDATE users SET failed_login_count = 0, locked_until = NULL, last_login_date = UTC_TIMESTAMP(), updated_by = p_user_id
      WHERE user_id = p_user_id;
    ELSE
      UPDATE users
      SET failed_login_count = failed_login_count + 1,
          locked_until = IF(failed_login_count + 1 >= 5, UTC_TIMESTAMP() + INTERVAL 15 MINUTE, locked_until)
      WHERE user_id = p_user_id;
    END IF;
  END IF;
  INSERT INTO login_history (user_id, login_id_entered, is_success, failure_reason, ip_address, device_name, inserted_by)
  VALUES (p_user_id, p_login_id, p_is_success, p_failure_reason, p_ip_address, p_device_name, p_user_id);
END;

-- What the browser keeps about the signed-in person.
CREATE PROCEDURE sp_auth_get_session_user(IN p_user_id BIGINT)
BEGIN
  SELECT u.user_id, u.business_id, b.business_name, r.role_code, u.full_name, u.mobile_no, u.email,
         u.preferred_language, COALESCE(b.currency_code, 'INR') AS currency_code, COALESCE(b.time_zone, 'Asia/Kolkata') AS time_zone,
         u.is_active, b.status AS business_status
  FROM users u
  JOIN roles r ON r.role_id = u.role_id
  LEFT JOIN businesses b ON b.business_id = u.business_id
  WHERE u.user_id = p_user_id;
END;

-- Ticked permission codes for a staff member.
CREATE PROCEDURE sp_auth_get_user_permissions(IN p_user_id BIGINT)
BEGIN
  SELECT p.permission_code
  FROM user_permissions up
  JOIN permissions p ON p.permission_id = up.permission_id AND p.is_active = 1
  WHERE up.user_id = p_user_id AND up.is_active = 1 AND up.is_allowed = 1
  ORDER BY p.sort_order;
END;

CREATE PROCEDURE sp_auth_get_password_hash(IN p_user_id BIGINT)
BEGIN
  SELECT password_hash FROM users WHERE user_id = p_user_id AND is_active = 1;
END;

CREATE PROCEDURE sp_auth_change_password(IN p_user_id BIGINT, IN p_password_hash VARCHAR(255))
BEGIN
  UPDATE users SET password_hash = p_password_hash, must_change_password = 0, updated_by = p_user_id
  WHERE user_id = p_user_id;
END;

CREATE PROCEDURE sp_auth_update_profile(IN p_user_id BIGINT, IN p_full_name VARCHAR(100), IN p_preferred_language VARCHAR(5))
BEGIN
  UPDATE users SET full_name = p_full_name, preferred_language = p_preferred_language, updated_by = p_user_id
  WHERE user_id = p_user_id;
END;

-- Owner signs up: business + owner + first free period.
-- Called inside a Java @Transactional method, so it is all or nothing.
CREATE PROCEDURE sp_auth_register_business(
  IN p_business_name VARCHAR(150), IN p_business_type VARCHAR(20), IN p_owner_name VARCHAR(100),
  IN p_mobile_no VARCHAR(15), IN p_email VARCHAR(150), IN p_password_hash VARCHAR(255),
  IN p_bill_prefix VARCHAR(10), IN p_plan_code VARCHAR(30), IN p_mobile_verified TINYINT, IN p_created_by BIGINT)
BEGIN
  DECLARE v_plan_id BIGINT;
  DECLARE v_owner_role BIGINT;
  DECLARE v_business_id BIGINT;
  DECLARE v_user_id BIGINT;

  SELECT plan_id INTO v_plan_id FROM plans WHERE plan_code = p_plan_code AND is_active = 1;
  SELECT role_id INTO v_owner_role FROM roles WHERE role_code = 'OWNER';

  INSERT INTO businesses (business_code, business_name, business_type, owner_name, mobile_no, email, bill_prefix,
                          plan_id, plan_start_date, status, inserted_by)
  VALUES (CONCAT('B', UNIX_TIMESTAMP(), FLOOR(RAND() * 1000)), p_business_name, p_business_type, p_owner_name,
          p_mobile_no, p_email, p_bill_prefix, v_plan_id, UTC_DATE(), 'ACTIVE', p_created_by);
  SET v_business_id = LAST_INSERT_ID();
  UPDATE businesses SET business_code = CONCAT('B', LPAD(v_business_id, 5, '0')) WHERE business_id = v_business_id;

  INSERT INTO users (business_id, role_id, full_name, mobile_no, email, password_hash, is_mobile_verified, inserted_by)
  VALUES (v_business_id, v_owner_role, p_owner_name, p_mobile_no, p_email, p_password_hash, p_mobile_verified, p_created_by);
  SET v_user_id = LAST_INSERT_ID();

  INSERT INTO business_subscriptions (business_id, plan_id, start_date, end_date, amount, payment_status, inserted_by)
  VALUES (v_business_id, v_plan_id, UTC_DATE(), UTC_DATE() + INTERVAL 1 MONTH, 0, 'WAIVED', COALESCE(p_created_by, v_user_id));

  INSERT INTO customers (business_id, customer_name, inserted_by) VALUES (v_business_id, 'Takeaway', v_user_id);

  SELECT v_business_id AS business_id, v_user_id AS user_id;
END;

CREATE PROCEDURE sp_auth_mobile_exists(IN p_mobile_no VARCHAR(15), IN p_except_user_id BIGINT)
BEGIN
  SELECT COUNT(*) AS cnt FROM users WHERE mobile_no = p_mobile_no AND (p_except_user_id IS NULL OR user_id <> p_except_user_id);
END;

-- ------------------------------------------------------------------ sessions

CREATE PROCEDURE sp_session_create(
  IN p_user_id BIGINT, IN p_business_id BIGINT, IN p_token_hash VARCHAR(255), IN p_device_name VARCHAR(150),
  IN p_ip_address VARCHAR(45), IN p_expires_at DATETIME)
BEGIN
  INSERT INTO user_sessions (user_id, business_id, token_hash, device_name, ip_address, login_date, last_seen_date, expires_at, inserted_by)
  VALUES (p_user_id, p_business_id, p_token_hash, p_device_name, p_ip_address, UTC_TIMESTAMP(), UTC_TIMESTAMP(), p_expires_at, p_user_id);
  SELECT LAST_INSERT_ID() AS session_id;
END;

-- Session still usable? (used on every request, so a logout works at once)
CREATE PROCEDURE sp_session_is_active(IN p_session_id BIGINT, IN p_user_id BIGINT)
BEGIN
  SELECT COUNT(*) AS cnt FROM user_sessions
  WHERE session_id = p_session_id AND user_id = p_user_id AND is_active = 1 AND logout_date IS NULL AND expires_at > UTC_TIMESTAMP();
END;

CREATE PROCEDURE sp_session_get_by_token(IN p_token_hash VARCHAR(255))
BEGIN
  SELECT session_id, user_id, business_id, expires_at FROM user_sessions
  WHERE token_hash = p_token_hash AND is_active = 1 AND logout_date IS NULL AND expires_at > UTC_TIMESTAMP();
END;

CREATE PROCEDURE sp_session_touch(IN p_session_id BIGINT)
BEGIN
  UPDATE user_sessions SET last_seen_date = UTC_TIMESTAMP() WHERE session_id = p_session_id;
END;

CREATE PROCEDURE sp_session_close(IN p_session_id BIGINT, IN p_user_id BIGINT)
BEGIN
  UPDATE user_sessions SET is_active = 0, logout_date = UTC_TIMESTAMP(), updated_by = p_user_id
  WHERE session_id = p_session_id AND user_id = p_user_id;
END;

-- Signs a person out everywhere (password change, login turned off).
CREATE PROCEDURE sp_session_close_all(IN p_user_id BIGINT, IN p_by BIGINT)
BEGIN
  UPDATE user_sessions SET is_active = 0, logout_date = UTC_TIMESTAMP(), updated_by = p_by
  WHERE user_id = p_user_id AND is_active = 1;
END;

-- ------------------------------------------------------------------ OTP

CREATE PROCEDURE sp_otp_create(IN p_mobile_no VARCHAR(15), IN p_purpose VARCHAR(20), IN p_otp_hash VARCHAR(255), IN p_minutes INT)
BEGIN
  -- only the newest code is valid
  UPDATE user_otps SET is_active = 0 WHERE mobile_no = p_mobile_no AND purpose = p_purpose AND is_active = 1;
  INSERT INTO user_otps (mobile_no, purpose, otp_hash, expires_at) VALUES (p_mobile_no, p_purpose, p_otp_hash, UTC_TIMESTAMP() + INTERVAL p_minutes MINUTE);
  SELECT LAST_INSERT_ID() AS otp_id;
END;

CREATE PROCEDURE sp_otp_get_latest(IN p_mobile_no VARCHAR(15), IN p_purpose VARCHAR(20))
BEGIN
  SELECT otp_id, otp_hash, expires_at, attempts, used_date FROM user_otps
  WHERE mobile_no = p_mobile_no AND purpose = p_purpose AND is_active = 1
  ORDER BY otp_id DESC LIMIT 1;
END;

CREATE PROCEDURE sp_otp_add_attempt(IN p_otp_id BIGINT)
BEGIN
  UPDATE user_otps SET attempts = attempts + 1 WHERE otp_id = p_otp_id;
END;

CREATE PROCEDURE sp_otp_mark_used(IN p_otp_id BIGINT)
BEGIN
  UPDATE user_otps SET used_date = UTC_TIMESTAMP() WHERE otp_id = p_otp_id;
END;

-- ------------------------------------------------------------------ page rights

-- Side menu + rights (database/README.md section 4): role page rights, and for
-- STAFF also the tick the page needs.
CREATE PROCEDURE sp_menu_binding(IN p_user_id BIGINT)
BEGIN
  SELECT p.page_code, p.route_path, p.menu_group, p.icon_name, p.show_in_menu, p.sort_order,
         rp.can_view, rp.can_add, rp.can_edit, rp.can_delete, rp.can_export
  FROM users u
  JOIN roles r ON r.role_id = u.role_id AND r.is_active = 1
  JOIN role_page_rights rp ON rp.role_id = u.role_id AND rp.is_active = 1 AND rp.can_view = 1
  JOIN pages p ON p.page_id = rp.page_id AND p.is_active = 1
  WHERE u.user_id = p_user_id AND u.is_active = 1
    AND ( r.role_code <> 'STAFF'
          OR p.required_permission_code IS NULL
          OR EXISTS (SELECT 1 FROM user_permissions up
                     JOIN permissions pm ON pm.permission_id = up.permission_id
                     WHERE up.user_id = u.user_id AND up.is_active = 1 AND up.is_allowed = 1
                       AND pm.permission_code = p.required_permission_code) )
  ORDER BY p.sort_order;
END;
