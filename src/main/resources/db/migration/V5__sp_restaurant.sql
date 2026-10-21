-- =============================================================================
--  Restaurant setup: menu groups, dishes, tables, customers.
--  Every procedure takes p_business_id and filters by it.
-- =============================================================================

-- ------------------------------------------------------------------ menu groups

CREATE PROCEDURE sp_category_get_all(IN p_business_id BIGINT)
BEGIN
  SELECT category_id, business_id, category_name, sort_order
  FROM menu_categories WHERE business_id = p_business_id AND is_active = 1
  ORDER BY sort_order, category_name;
END;

CREATE PROCEDURE sp_category_save(IN p_category_id BIGINT, IN p_business_id BIGINT, IN p_category_name VARCHAR(100), IN p_by BIGINT)
BEGIN
  IF EXISTS (SELECT 1 FROM menu_categories WHERE business_id = p_business_id AND is_active = 1
             AND LOWER(category_name) = LOWER(p_category_name) AND category_id <> COALESCE(p_category_id, 0)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This group already exists.';
  END IF;
  IF p_category_id IS NULL OR p_category_id = 0 THEN
    INSERT INTO menu_categories (business_id, category_name, sort_order, inserted_by)
    VALUES (p_business_id, p_category_name,
            (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM menu_categories c2 WHERE c2.business_id = p_business_id), p_by);
    SELECT LAST_INSERT_ID() AS category_id;
  ELSE
    UPDATE menu_categories SET category_name = p_category_name, updated_by = p_by
    WHERE category_id = p_category_id AND business_id = p_business_id AND is_active = 1;
    SELECT p_category_id AS category_id;
  END IF;
END;

CREATE PROCEDURE sp_category_delete(IN p_category_id BIGINT, IN p_business_id BIGINT, IN p_by BIGINT)
BEGIN
  IF EXISTS (SELECT 1 FROM menu_items WHERE category_id = p_category_id AND business_id = p_business_id AND is_active = 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Move or remove the dishes in this group first.';
  END IF;
  UPDATE menu_categories SET is_active = 0, updated_by = p_by WHERE category_id = p_category_id AND business_id = p_business_id;
END;

-- ------------------------------------------------------------------ dishes

CREATE PROCEDURE sp_item_get_all(IN p_business_id BIGINT)
BEGIN
  SELECT item_id, business_id, category_id, COALESCE(item_code, '') AS item_code, item_name, COALESCE(image_url, '') AS image_url,
         unit_name, price, tax_rate, is_veg, is_available
  FROM menu_items WHERE business_id = p_business_id AND is_active = 1
  ORDER BY item_name;
END;

CREATE PROCEDURE sp_item_get_by_id(IN p_item_id BIGINT, IN p_business_id BIGINT)
BEGIN
  SELECT item_id, business_id, category_id, COALESCE(item_code, '') AS item_code, item_name, COALESCE(image_url, '') AS image_url,
         unit_name, price, tax_rate, is_veg, is_available
  FROM menu_items WHERE item_id = p_item_id AND business_id = p_business_id AND is_active = 1;
END;

-- Saves a dish; when the price changes, also writes menu_item_price_history.
CREATE PROCEDURE sp_item_save(
  IN p_item_id BIGINT, IN p_business_id BIGINT, IN p_category_id BIGINT, IN p_item_code VARCHAR(30), IN p_item_name VARCHAR(150),
  IN p_image_url VARCHAR(500), IN p_unit_name VARCHAR(20), IN p_price DECIMAL(12,2), IN p_tax_rate DECIMAL(5,2),
  IN p_is_veg TINYINT, IN p_is_available TINYINT, IN p_by BIGINT)
BEGIN
  DECLARE v_old_price DECIMAL(12,2);
  DECLARE v_old_tax DECIMAL(5,2);
  IF p_category_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM menu_categories WHERE category_id = p_category_id
                                               AND business_id = p_business_id AND is_active = 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Choose a valid group.';
  END IF;
  IF p_item_id IS NULL OR p_item_id = 0 THEN
    INSERT INTO menu_items (business_id, category_id, item_code, item_name, image_url, unit_name, price, tax_rate, is_veg, is_available, inserted_by)
    VALUES (p_business_id, p_category_id, NULLIF(p_item_code, ''), p_item_name, NULLIF(p_image_url, ''), p_unit_name, p_price, p_tax_rate,
            p_is_veg, p_is_available, p_by);
    SELECT LAST_INSERT_ID() AS item_id, 0 AS price_changed;
  ELSE
    SELECT price, tax_rate INTO v_old_price, v_old_tax FROM menu_items
    WHERE item_id = p_item_id AND business_id = p_business_id AND is_active = 1;
    IF v_old_price IS NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Dish not found.';
    END IF;
    UPDATE menu_items
    SET category_id = p_category_id, item_code = NULLIF(p_item_code, ''), item_name = p_item_name, image_url = NULLIF(p_image_url, ''),
        unit_name = p_unit_name, price = p_price, tax_rate = p_tax_rate, is_veg = p_is_veg, is_available = p_is_available, updated_by = p_by
    WHERE item_id = p_item_id AND business_id = p_business_id;
    IF v_old_price <> p_price OR v_old_tax <> p_tax_rate THEN
      INSERT INTO menu_item_price_history (business_id, item_id, old_price, new_price, old_tax_rate, new_tax_rate, inserted_by)
      VALUES (p_business_id, p_item_id, v_old_price, p_price, v_old_tax, p_tax_rate, p_by);
    END IF;
    SELECT p_item_id AS item_id, IF(v_old_price <> p_price, 1, 0) AS price_changed;
  END IF;
END;

CREATE PROCEDURE sp_item_set_availability(IN p_item_id BIGINT, IN p_business_id BIGINT, IN p_is_available TINYINT, IN p_by BIGINT)
BEGIN
  UPDATE menu_items SET is_available = p_is_available, updated_by = p_by
  WHERE item_id = p_item_id AND business_id = p_business_id AND is_active = 1;
  SELECT ROW_COUNT() AS cnt;
END;

-- ------------------------------------------------------------------ tables

-- Floor screen: every table with the numbers of its current order.
CREATE PROCEDURE sp_table_get_floor(IN p_business_id BIGINT)
BEGIN
  SELECT t.table_id, t.business_id, t.table_name, COALESCE(t.area_name, '') AS area_name, t.seats, t.table_status, t.current_order_id,
         COALESCE(o.total_amount, 0) AS order_total, COALESCE(o.balance_amount, 0) AS order_balance,
         COALESCE((SELECT SUM(oi.quantity) FROM order_items oi WHERE oi.order_id = o.order_id AND oi.is_active = 1), 0) AS item_count,
         IF(o.order_id IS NULL, NULL, IF(o.order_status = 'OPEN', o.inserted_date, o.billed_date)) AS since
  FROM dining_tables t
  LEFT JOIN orders o ON o.order_id = t.current_order_id
  WHERE t.business_id = p_business_id AND t.is_active = 1
  ORDER BY t.sort_order, CAST(REGEXP_SUBSTR(t.table_name, '[0-9]+') AS UNSIGNED), t.table_name;
END;

CREATE PROCEDURE sp_table_count(IN p_business_id BIGINT)
BEGIN
  SELECT COUNT(*) AS cnt FROM dining_tables WHERE business_id = p_business_id AND is_active = 1;
END;

CREATE PROCEDURE sp_table_add(IN p_business_id BIGINT, IN p_table_name VARCHAR(50), IN p_by BIGINT)
BEGIN
  DECLARE v_next INT;
  SELECT COALESCE(MAX(CAST(REGEXP_SUBSTR(table_name, '[0-9]+') AS UNSIGNED)), 0) + 1 INTO v_next
  FROM dining_tables WHERE business_id = p_business_id AND is_active = 1;
  INSERT INTO dining_tables (business_id, table_name, seats, table_status, sort_order, inserted_by)
  VALUES (p_business_id, COALESCE(NULLIF(p_table_name, ''), CONCAT('Table ', v_next)), 4, 'AVAILABLE', v_next, p_by);
  SELECT LAST_INSERT_ID() AS table_id;
END;

CREATE PROCEDURE sp_table_remove(IN p_table_id BIGINT, IN p_business_id BIGINT, IN p_by BIGINT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM dining_tables WHERE table_id = p_table_id AND business_id = p_business_id AND is_active = 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Table not found.';
  END IF;
  IF EXISTS (SELECT 1 FROM dining_tables WHERE table_id = p_table_id AND table_status <> 'AVAILABLE') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Finish the order on this table first.';
  END IF;
  -- keep the name free for a new table: rename the removed one
  UPDATE dining_tables SET is_active = 0, table_name = CONCAT(table_name, ' #', table_id), updated_by = p_by
  WHERE table_id = p_table_id AND business_id = p_business_id;
END;

CREATE PROCEDURE sp_table_get_by_id(IN p_table_id BIGINT, IN p_business_id BIGINT)
BEGIN
  SELECT table_id, business_id, table_name, table_status, current_order_id
  FROM dining_tables WHERE table_id = p_table_id AND business_id = p_business_id AND is_active = 1;
END;

CREATE PROCEDURE sp_table_set_status(IN p_table_id BIGINT, IN p_status VARCHAR(20), IN p_order_id BIGINT, IN p_by BIGINT)
BEGIN
  UPDATE dining_tables SET table_status = p_status, current_order_id = p_order_id, updated_by = p_by WHERE table_id = p_table_id;
END;

-- ------------------------------------------------------------------ customers

CREATE PROCEDURE sp_customer_get_all(IN p_business_id BIGINT)
BEGIN
  SELECT c.customer_id, c.business_id, c.customer_name, COALESCE(c.mobile_no, '') AS mobile_no, COALESCE(c.email, '') AS email,
         COALESCE(c.address_line, '') AS address_line, COALESCE(c.gstin, '') AS gstin,
         COUNT(o.order_id) AS bill_count, COALESCE(SUM(o.total_amount), 0) AS billed, COALESCE(SUM(o.balance_amount), 0) AS balance
  FROM customers c
  LEFT JOIN orders o ON o.customer_id = c.customer_id AND o.is_active = 1 AND o.order_status IN ('BILLED', 'PART_PAID', 'PAID', 'REFUNDED')
  WHERE c.business_id = p_business_id AND c.is_active = 1
  GROUP BY c.customer_id
  ORDER BY c.customer_name;
END;

CREATE PROCEDURE sp_customer_get_by_id(IN p_customer_id BIGINT, IN p_business_id BIGINT)
BEGIN
  SELECT customer_id, business_id, customer_name FROM customers
  WHERE customer_id = p_customer_id AND business_id = p_business_id AND is_active = 1;
END;

CREATE PROCEDURE sp_customer_save(
  IN p_customer_id BIGINT, IN p_business_id BIGINT, IN p_customer_name VARCHAR(150), IN p_mobile_no VARCHAR(15),
  IN p_email VARCHAR(150), IN p_address_line VARCHAR(255), IN p_gstin VARCHAR(20), IN p_by BIGINT)
BEGIN
  IF p_customer_id IS NULL OR p_customer_id = 0 THEN
    INSERT INTO customers (business_id, customer_name, mobile_no, email, address_line, gstin, inserted_by)
    VALUES (p_business_id, p_customer_name, NULLIF(p_mobile_no, ''), NULLIF(p_email, ''), NULLIF(p_address_line, ''), NULLIF(p_gstin, ''), p_by);
    SELECT LAST_INSERT_ID() AS customer_id;
  ELSE
    UPDATE customers SET customer_name = p_customer_name, mobile_no = NULLIF(p_mobile_no, ''), email = NULLIF(p_email, ''),
                         address_line = NULLIF(p_address_line, ''), gstin = NULLIF(p_gstin, ''), updated_by = p_by
    WHERE customer_id = p_customer_id AND business_id = p_business_id AND is_active = 1;
    IF ROW_COUNT() = 0 AND NOT EXISTS (SELECT 1 FROM customers WHERE customer_id = p_customer_id AND business_id = p_business_id AND is_active = 1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer not found.';
    END IF;
    SELECT p_customer_id AS customer_id;
  END IF;
END;

CREATE PROCEDURE sp_customer_delete(IN p_customer_id BIGINT, IN p_business_id BIGINT, IN p_by BIGINT)
BEGIN
  IF EXISTS (SELECT 1 FROM orders WHERE customer_id = p_customer_id AND is_active = 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This customer has bills and cannot be removed.';
  END IF;
  UPDATE customers SET is_active = 0, updated_by = p_by WHERE customer_id = p_customer_id AND business_id = p_business_id;
END;
