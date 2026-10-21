-- =============================================================================
--  Platform: businesses, plans, users (team + all users)
-- =============================================================================

-- ------------------------------------------------------------------ businesses

CREATE PROCEDURE sp_business_get_all()
BEGIN
  SELECT b.business_id, b.business_code, b.business_name, b.business_type, COALESCE(b.owner_name, '') AS owner_name,
         COALESCE(b.mobile_no, '') AS mobile_no, COALESCE(b.email, '') AS email, COALESCE(b.address_line, '') AS address_line,
         COALESCE(b.gstin, '') AS gstin, b.currency_code, b.default_language, b.bill_prefix, b.next_bill_no,
         b.default_tax_rate, b.service_charge_pct, b.service_charge_label, COALESCE(b.bill_footer_note, '') AS bill_footer_note,
         b.plan_id, b.status, COALESCE(b.status_reason, '') AS status_reason, b.inserted_date,
         COALESCE(pl.plan_name, '') AS plan_name, COALESCE(pl.max_users, 0) AS max_users,
         (SELECT COUNT(*) FROM users u WHERE u.business_id = b.business_id AND u.is_active = 1) AS user_count,
         (SELECT COUNT(*) FROM orders o WHERE o.business_id = b.business_id AND o.bill_no IS NOT NULL AND o.is_active = 1) AS bill_count,
         (SELECT COALESCE(SUM(IF(p.payment_type = 'REFUND', -p.amount, p.amount)), 0) FROM payments p
           WHERE p.business_id = b.business_id AND p.is_active = 1) AS collected
  FROM businesses b
  LEFT JOIN plans pl ON pl.plan_id = b.plan_id
  WHERE b.is_active = 1
  ORDER BY b.business_name;
END;

CREATE PROCEDURE sp_business_get_by_id(IN p_business_id BIGINT)
BEGIN
  SELECT b.business_id, b.business_code, b.business_name, b.business_type, COALESCE(b.owner_name, '') AS owner_name,
         COALESCE(b.mobile_no, '') AS mobile_no, COALESCE(b.email, '') AS email, COALESCE(b.address_line, '') AS address_line,
         COALESCE(b.gstin, '') AS gstin, b.currency_code, b.default_language, b.bill_prefix, b.next_bill_no,
         b.default_tax_rate, b.service_charge_pct, b.service_charge_label, COALESCE(b.bill_footer_note, '') AS bill_footer_note,
         b.plan_id, b.status, COALESCE(b.status_reason, '') AS status_reason, b.inserted_date, b.time_zone
  FROM businesses b
  WHERE b.business_id = p_business_id AND b.is_active = 1;
END;

CREATE PROCEDURE sp_business_update(
  IN p_business_id BIGINT, IN p_business_name VARCHAR(150), IN p_business_type VARCHAR(20), IN p_mobile_no VARCHAR(15),
  IN p_email VARCHAR(150), IN p_address_line VARCHAR(255), IN p_gstin VARCHAR(20), IN p_plan_id BIGINT,
  IN p_bill_prefix VARCHAR(10), IN p_updated_by BIGINT)
BEGIN
  UPDATE businesses
  SET business_name = p_business_name, business_type = p_business_type, mobile_no = p_mobile_no, email = p_email,
      address_line = p_address_line, gstin = p_gstin, plan_id = p_plan_id, bill_prefix = p_bill_prefix, updated_by = p_updated_by
  WHERE business_id = p_business_id AND is_active = 1;
END;

CREATE PROCEDURE sp_business_set_status(IN p_business_id BIGINT, IN p_status VARCHAR(20), IN p_reason VARCHAR(255), IN p_updated_by BIGINT)
BEGIN
  UPDATE businesses SET status = p_status, status_reason = p_reason, updated_by = p_updated_by
  WHERE business_id = p_business_id AND is_active = 1;
END;

CREATE PROCEDURE sp_business_save_settings(
  IN p_business_id BIGINT, IN p_business_name VARCHAR(150), IN p_mobile_no VARCHAR(15), IN p_email VARCHAR(150),
  IN p_address_line VARCHAR(255), IN p_gstin VARCHAR(20), IN p_bill_prefix VARCHAR(10), IN p_default_tax_rate DECIMAL(5,2),
  IN p_service_charge_pct DECIMAL(5,2), IN p_service_charge_label VARCHAR(50), IN p_bill_footer_note VARCHAR(255),
  IN p_default_language VARCHAR(5), IN p_updated_by BIGINT)
BEGIN
  UPDATE businesses
  SET business_name = p_business_name, mobile_no = p_mobile_no, email = p_email, address_line = p_address_line,
      gstin = p_gstin, bill_prefix = p_bill_prefix, default_tax_rate = p_default_tax_rate,
      service_charge_pct = p_service_charge_pct, service_charge_label = p_service_charge_label,
      bill_footer_note = p_bill_footer_note, default_language = p_default_language, updated_by = p_updated_by
  WHERE business_id = p_business_id AND is_active = 1;
END;

CREATE PROCEDURE sp_platform_stats()
BEGIN
  SELECT
    (SELECT COUNT(*) FROM businesses WHERE is_active = 1) AS businesses,
    (SELECT COUNT(*) FROM businesses WHERE is_active = 1 AND status = 'ACTIVE') AS active_businesses,
    (SELECT COUNT(*) FROM users u JOIN roles r ON r.role_id = u.role_id WHERE u.is_active = 1 AND r.role_code <> 'SUPER_ADMIN') AS users,
    (SELECT COUNT(*) FROM orders WHERE bill_no IS NOT NULL AND is_active = 1) AS bills,
    (SELECT COALESCE(SUM(pl.monthly_price), 0) FROM businesses b JOIN plans pl ON pl.plan_id = b.plan_id
      WHERE b.is_active = 1 AND b.status = 'ACTIVE') AS monthly_revenue,
    (SELECT COALESCE(SUM(IF(payment_type = 'REFUND', -amount, amount)), 0) FROM payments WHERE is_active = 1) AS collected;
END;

-- ------------------------------------------------------------------ plans

CREATE PROCEDURE sp_plan_get_all()
BEGIN
  SELECT plan_id, plan_code, plan_name, COALESCE(description, '') AS description, monthly_price, max_users, max_tables
  FROM plans WHERE is_active = 1 ORDER BY sort_order, monthly_price;
END;

CREATE PROCEDURE sp_plan_save(
  IN p_plan_id BIGINT, IN p_plan_code VARCHAR(30), IN p_plan_name VARCHAR(100), IN p_description VARCHAR(255),
  IN p_monthly_price DECIMAL(12,2), IN p_max_users INT, IN p_max_tables INT, IN p_by BIGINT)
BEGIN
  IF p_plan_id IS NULL OR p_plan_id = 0 THEN
    INSERT INTO plans (plan_code, plan_name, description, monthly_price, max_users, max_tables, sort_order, inserted_by)
    VALUES (p_plan_code, p_plan_name, p_description, p_monthly_price, p_max_users, p_max_tables,
            (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM plans p2), p_by);
    SELECT LAST_INSERT_ID() AS plan_id;
  ELSE
    UPDATE plans SET plan_name = p_plan_name, description = p_description, monthly_price = p_monthly_price,
                     max_users = p_max_users, max_tables = p_max_tables, updated_by = p_by
    WHERE plan_id = p_plan_id AND is_active = 1;
    SELECT p_plan_id AS plan_id;
  END IF;
END;

CREATE PROCEDURE sp_plan_in_use(IN p_plan_id BIGINT)
BEGIN
  SELECT COUNT(*) AS cnt FROM businesses WHERE plan_id = p_plan_id AND is_active = 1;
END;

CREATE PROCEDURE sp_plan_delete(IN p_plan_id BIGINT, IN p_by BIGINT)
BEGIN
  UPDATE plans SET is_active = 0, updated_by = p_by WHERE plan_id = p_plan_id;
END;

-- ------------------------------------------------------------------ users

-- p_business_id NULL = platform admin sees everyone
CREATE PROCEDURE sp_user_get_all(IN p_business_id BIGINT)
BEGIN
  SELECT u.user_id, u.business_id, b.business_name, r.role_code, u.full_name, u.mobile_no, COALESCE(u.email, '') AS email,
         u.preferred_language, u.is_active, u.last_login_date,
         COALESCE((SELECT GROUP_CONCAT(pm.permission_code ORDER BY pm.sort_order)
                   FROM user_permissions up JOIN permissions pm ON pm.permission_id = up.permission_id
                   WHERE up.user_id = u.user_id AND up.is_active = 1 AND up.is_allowed = 1), '') AS permission_codes
  FROM users u
  JOIN roles r ON r.role_id = u.role_id
  LEFT JOIN businesses b ON b.business_id = u.business_id
  WHERE p_business_id IS NULL OR u.business_id = p_business_id
  ORDER BY r.sort_order, u.full_name;
END;

CREATE PROCEDURE sp_user_get_by_id(IN p_user_id BIGINT)
BEGIN
  SELECT u.user_id, u.business_id, r.role_code, u.full_name, u.mobile_no, u.is_active
  FROM users u JOIN roles r ON r.role_id = u.role_id WHERE u.user_id = p_user_id;
END;

CREATE PROCEDURE sp_user_count_active(IN p_business_id BIGINT)
BEGIN
  SELECT COUNT(*) AS cnt FROM users WHERE business_id = p_business_id AND is_active = 1;
END;

CREATE PROCEDURE sp_user_save(
  IN p_user_id BIGINT, IN p_business_id BIGINT, IN p_role_code VARCHAR(30), IN p_full_name VARCHAR(100),
  IN p_mobile_no VARCHAR(15), IN p_email VARCHAR(150), IN p_password_hash VARCHAR(255), IN p_by BIGINT)
BEGIN
  DECLARE v_role_id BIGINT;
  SELECT role_id INTO v_role_id FROM roles WHERE role_code = p_role_code;
  IF p_user_id IS NULL OR p_user_id = 0 THEN
    INSERT INTO users (business_id, role_id, full_name, mobile_no, email, password_hash, must_change_password, inserted_by)
    VALUES (p_business_id, v_role_id, p_full_name, p_mobile_no, NULLIF(p_email, ''), p_password_hash, 1, p_by);
    SELECT LAST_INSERT_ID() AS user_id;
  ELSE
    UPDATE users
    SET business_id = p_business_id, role_id = v_role_id, full_name = p_full_name, mobile_no = p_mobile_no,
        email = NULLIF(p_email, ''), password_hash = COALESCE(p_password_hash, password_hash), updated_by = p_by
    WHERE user_id = p_user_id;
    SELECT p_user_id AS user_id;
  END IF;
END;

-- Replace a staff member's ticks with the given comma list (e.g. 'ORDERS_TAKE,PAYMENTS_TAKE').
CREATE PROCEDURE sp_user_set_permissions(IN p_user_id BIGINT, IN p_business_id BIGINT, IN p_codes VARCHAR(500), IN p_by BIGINT)
BEGIN
  UPDATE user_permissions SET is_active = 0, updated_by = p_by WHERE user_id = p_user_id AND is_active = 1;
  INSERT INTO user_permissions (business_id, user_id, permission_id, is_allowed, is_active, inserted_by)
  SELECT p_business_id, p_user_id, pm.permission_id, 1, 1, p_by
  FROM permissions pm
  WHERE FIND_IN_SET(pm.permission_code, p_codes) > 0
  ON DUPLICATE KEY UPDATE is_allowed = 1, is_active = 1, updated_by = p_by;
END;

CREATE PROCEDURE sp_user_set_status(IN p_user_id BIGINT, IN p_is_active TINYINT, IN p_by BIGINT)
BEGIN
  UPDATE users SET is_active = p_is_active, updated_by = p_by WHERE user_id = p_user_id;
END;
