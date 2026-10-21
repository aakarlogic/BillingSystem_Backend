-- =============================================================================
--  Billing: orders (bills), order items, payments, reports.
--  Bill math is done in Java (BillingCalculator); these only store and read.
-- =============================================================================

-- One row per order with the names the screens show. Used by many procedures.
CREATE VIEW v_order_list AS
SELECT o.order_id, o.business_id, o.bill_no, o.order_type, o.table_id, t.table_name, o.customer_id, o.customer_name,
       o.order_status, o.business_date, o.sub_total, o.discount_type, o.discount_value, o.discount_amount, o.tax_amount,
       o.service_charge_pct, o.service_charge_amount, o.total_amount, o.paid_amount, o.balance_amount,
       COALESCE(o.notes, '') AS notes, o.waiter_user_id, COALESCE(w.full_name, '') AS waiter_name, o.billed_by, o.billed_date,
       o.paid_date, COALESCE(o.cancel_reason, '') AS cancel_reason, o.inserted_date, o.is_active,
       COALESCE((SELECT SUM(oi.quantity) FROM order_items oi WHERE oi.order_id = o.order_id AND oi.is_active = 1), 0) AS item_count
FROM orders o
LEFT JOIN dining_tables t ON t.table_id = o.table_id
LEFT JOIN users w ON w.user_id = o.waiter_user_id;

-- ------------------------------------------------------------------ orders

CREATE PROCEDURE sp_order_get_all(IN p_business_id BIGINT, IN p_status VARCHAR(20), IN p_search VARCHAR(100), IN p_mine_user_id BIGINT)
BEGIN
  SELECT * FROM v_order_list v
  WHERE v.business_id = p_business_id AND v.is_active = 1
    AND (p_status IS NULL OR p_status = '' OR p_status = 'ALL' OR v.order_status = p_status)
    AND (p_mine_user_id IS NULL OR v.waiter_user_id = p_mine_user_id OR v.billed_by = p_mine_user_id)
    AND (p_search IS NULL OR p_search = ''
         OR CONCAT_WS(' ', v.bill_no, v.customer_name, v.table_name, v.waiter_name) LIKE CONCAT('%', p_search, '%'))
  ORDER BY v.order_id DESC
  LIMIT 1000;
END;

CREATE PROCEDURE sp_order_get_by_id(IN p_order_id BIGINT, IN p_business_id BIGINT)
BEGIN
  SELECT * FROM v_order_list WHERE order_id = p_order_id AND business_id = p_business_id AND is_active = 1;
END;

CREATE PROCEDURE sp_order_items_get(IN p_order_id BIGINT)
BEGIN
  SELECT order_item_id, item_id, item_name, COALESCE(unit_name, '') AS unit_name, quantity, unit_price, line_amount,
         discount_amount, tax_rate, tax_amount, line_total
  FROM order_items WHERE order_id = p_order_id AND is_active = 1
  ORDER BY order_item_id;
END;

-- Insert (p_order_id NULL) or update an OPEN order's header and amounts.
CREATE PROCEDURE sp_order_save_header(
  IN p_order_id BIGINT, IN p_business_id BIGINT, IN p_order_type VARCHAR(20), IN p_table_id BIGINT, IN p_customer_id BIGINT,
  IN p_customer_name VARCHAR(150), IN p_business_date DATE, IN p_sub_total DECIMAL(12,2), IN p_discount_type VARCHAR(10),
  IN p_discount_value DECIMAL(12,2), IN p_discount_amount DECIMAL(12,2), IN p_discount_reason VARCHAR(255),
  IN p_taxable_amount DECIMAL(12,2), IN p_tax_amount DECIMAL(12,2), IN p_service_charge_pct DECIMAL(5,2),
  IN p_service_charge_amount DECIMAL(12,2), IN p_total_amount DECIMAL(12,2), IN p_notes VARCHAR(255), IN p_by BIGINT)
BEGIN
  IF p_order_id IS NULL OR p_order_id = 0 THEN
    INSERT INTO orders (business_id, order_type, table_id, customer_id, customer_name, order_status, business_date, sub_total,
                        discount_type, discount_value, discount_amount, discount_reason, taxable_amount, tax_amount,
                        service_charge_pct, service_charge_amount, total_amount, paid_amount, balance_amount, notes,
                        waiter_user_id, inserted_by)
    VALUES (p_business_id, p_order_type, p_table_id, p_customer_id, p_customer_name, 'OPEN', p_business_date, p_sub_total,
            p_discount_type, p_discount_value, p_discount_amount, NULLIF(p_discount_reason, ''), p_taxable_amount, p_tax_amount,
            p_service_charge_pct, p_service_charge_amount, p_total_amount, 0, 0, NULLIF(p_notes, ''), p_by, p_by);
    SELECT LAST_INSERT_ID() AS order_id;
  ELSE
    UPDATE orders
    SET order_type = p_order_type, table_id = p_table_id, customer_id = p_customer_id, customer_name = p_customer_name,
        sub_total = p_sub_total, discount_type = p_discount_type, discount_value = p_discount_value,
        discount_amount = p_discount_amount, discount_reason = NULLIF(p_discount_reason, ''), taxable_amount = p_taxable_amount,
        tax_amount = p_tax_amount, service_charge_pct = p_service_charge_pct, service_charge_amount = p_service_charge_amount,
        total_amount = p_total_amount, notes = NULLIF(p_notes, ''), updated_by = p_by
    WHERE order_id = p_order_id AND business_id = p_business_id AND order_status = 'OPEN' AND is_active = 1;
    IF ROW_COUNT() = 0 AND NOT EXISTS (SELECT 1 FROM orders WHERE order_id = p_order_id AND business_id = p_business_id
                                       AND order_status = 'OPEN' AND is_active = 1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This bill is already made and cannot be changed.';
    END IF;
    SELECT p_order_id AS order_id;
  END IF;
END;

-- Lines are replaced on every save of an open order (old ones are switched off, never deleted).
CREATE PROCEDURE sp_order_items_clear(IN p_order_id BIGINT, IN p_by BIGINT)
BEGIN
  UPDATE order_items SET is_active = 0, updated_by = p_by WHERE order_id = p_order_id AND is_active = 1;
END;

CREATE PROCEDURE sp_order_item_add(
  IN p_business_id BIGINT, IN p_order_id BIGINT, IN p_item_id BIGINT, IN p_item_name VARCHAR(150), IN p_unit_name VARCHAR(20),
  IN p_quantity DECIMAL(10,3), IN p_unit_price DECIMAL(12,2), IN p_line_amount DECIMAL(12,2), IN p_discount_amount DECIMAL(12,2),
  IN p_tax_rate DECIMAL(5,2), IN p_tax_amount DECIMAL(12,2), IN p_line_total DECIMAL(12,2), IN p_by BIGINT)
BEGIN
  INSERT INTO order_items (business_id, order_id, item_id, item_name, unit_name, quantity, unit_price, line_amount, discount_amount,
                           tax_rate, tax_amount, line_total, added_by, inserted_by)
  VALUES (p_business_id, p_order_id, p_item_id, p_item_name, p_unit_name, p_quantity, p_unit_price, p_line_amount, p_discount_amount,
          p_tax_rate, p_tax_amount, p_line_total, p_by, p_by);
END;

-- Gives the next bill number. FOR UPDATE locks the business row, so two cashiers never
-- get the same number. Called inside a Java @Transactional method.
CREATE PROCEDURE sp_order_make_bill(IN p_order_id BIGINT, IN p_business_id BIGINT, IN p_by BIGINT)
BEGIN
  DECLARE v_prefix VARCHAR(10);
  DECLARE v_next INT;
  DECLARE v_total DECIMAL(12,2);
  DECLARE v_bill_no VARCHAR(30);

  SELECT total_amount INTO v_total FROM orders
  WHERE order_id = p_order_id AND business_id = p_business_id AND order_status = 'OPEN' AND is_active = 1 FOR UPDATE;
  IF v_total IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This bill is already made.';
  END IF;
  SELECT bill_prefix, next_bill_no INTO v_prefix, v_next FROM businesses WHERE business_id = p_business_id FOR UPDATE;
  SET v_bill_no = CONCAT(v_prefix, LPAD(v_next, 5, '0'));
  UPDATE businesses SET next_bill_no = v_next + 1 WHERE business_id = p_business_id;
  UPDATE orders
  SET bill_no = v_bill_no, order_status = IF(v_total = 0, 'PAID', 'BILLED'), billed_by = p_by, billed_date = UTC_TIMESTAMP(),
      paid_date = IF(v_total = 0, UTC_TIMESTAMP(), NULL), balance_amount = v_total, updated_by = p_by
  WHERE order_id = p_order_id;
  SELECT v_bill_no AS bill_no;
END;

CREATE PROCEDURE sp_order_cancel(IN p_order_id BIGINT, IN p_business_id BIGINT, IN p_reason VARCHAR(255), IN p_by BIGINT)
BEGIN
  UPDATE orders
  SET order_status = 'CANCELLED', cancelled_by = p_by, cancelled_date = UTC_TIMESTAMP(), cancel_reason = p_reason,
      balance_amount = 0, updated_by = p_by
  WHERE order_id = p_order_id AND business_id = p_business_id AND order_status <> 'CANCELLED' AND is_active = 1;
END;

CREATE PROCEDURE sp_order_update_money(IN p_order_id BIGINT, IN p_paid DECIMAL(12,2), IN p_balance DECIMAL(12,2), IN p_status VARCHAR(20), IN p_by BIGINT)
BEGIN
  UPDATE orders
  SET paid_amount = p_paid, balance_amount = p_balance, order_status = p_status,
      paid_date = IF(p_status = 'PAID', COALESCE(paid_date, UTC_TIMESTAMP()), NULL), updated_by = p_by
  WHERE order_id = p_order_id;
END;

-- ------------------------------------------------------------------ payments

-- Names match the Payment shape the app shows.
CREATE VIEW v_payment_list AS
SELECT p.payment_id, p.business_id, p.order_id, o.bill_no, p.payment_type, p.payment_mode, p.amount, p.cash_received,
       p.change_returned, COALESCE(p.reason, '') AS reason, IF(EXISTS (SELECT 1 FROM payments r WHERE r.refund_of_payment_id = p.payment_id
       AND r.is_active = 1), 1, 0) AS is_refunded, COALESCE(u.full_name, '') AS received_by_name, p.received_date,
       p.business_date, p.received_by, p.is_active
FROM payments p
JOIN orders o ON o.order_id = p.order_id
LEFT JOIN users u ON u.user_id = p.received_by;

CREATE PROCEDURE sp_payment_get_by_order(IN p_order_id BIGINT)
BEGIN
  SELECT * FROM v_payment_list WHERE order_id = p_order_id AND is_active = 1 ORDER BY payment_id;
END;

CREATE PROCEDURE sp_payment_get_all(IN p_business_id BIGINT, IN p_limit INT)
BEGIN
  SELECT * FROM v_payment_list WHERE business_id = p_business_id AND is_active = 1 ORDER BY payment_id DESC LIMIT p_limit;
END;

CREATE PROCEDURE sp_payment_get_by_id(IN p_payment_id BIGINT, IN p_business_id BIGINT)
BEGIN
  SELECT * FROM v_payment_list WHERE payment_id = p_payment_id AND business_id = p_business_id AND is_active = 1;
END;

-- request_key is UNIQUE per business: a second tap with the same key fails (duplicate key).
CREATE PROCEDURE sp_payment_save(
  IN p_business_id BIGINT, IN p_order_id BIGINT, IN p_payment_type VARCHAR(10), IN p_payment_mode VARCHAR(10),
  IN p_amount DECIMAL(12,2), IN p_cash_received DECIMAL(12,2), IN p_change_returned DECIMAL(12,2),
  IN p_refund_of_payment_id BIGINT, IN p_reason VARCHAR(255), IN p_business_date DATE, IN p_request_key VARCHAR(64), IN p_by BIGINT)
BEGIN
  INSERT INTO payments (business_id, order_id, payment_type, payment_mode, amount, cash_received, change_returned,
                        refund_of_payment_id, reason, business_date, request_key, received_by, received_date, inserted_by)
  VALUES (p_business_id, p_order_id, p_payment_type, p_payment_mode, p_amount, p_cash_received, p_change_returned,
          p_refund_of_payment_id, NULLIF(p_reason, ''), p_business_date, p_request_key, p_by, UTC_TIMESTAMP(), p_by);
  SELECT LAST_INSERT_ID() AS payment_id;
END;

-- ------------------------------------------------------------------ reports

CREATE PROCEDURE sp_report_orders(IN p_business_id BIGINT, IN p_from DATE, IN p_to DATE)
BEGIN
  SELECT * FROM v_order_list
  WHERE business_id = p_business_id AND is_active = 1 AND business_date BETWEEN p_from AND p_to;
END;

-- Dish lines of bills that count as sales, for best sellers.
CREATE PROCEDURE sp_report_items(IN p_business_id BIGINT, IN p_from DATE, IN p_to DATE)
BEGIN
  SELECT oi.item_name, SUM(oi.quantity) AS quantity, SUM(oi.line_amount - oi.discount_amount) AS sales
  FROM order_items oi
  JOIN orders o ON o.order_id = oi.order_id
  WHERE o.business_id = p_business_id AND o.is_active = 1 AND oi.is_active = 1
    AND o.order_status IN ('BILLED', 'PART_PAID', 'PAID', 'REFUNDED') AND o.business_date BETWEEN p_from AND p_to
  GROUP BY oi.item_name;
END;

CREATE PROCEDURE sp_report_payments(IN p_business_id BIGINT, IN p_from DATE, IN p_to DATE)
BEGIN
  SELECT * FROM v_payment_list
  WHERE business_id = p_business_id AND is_active = 1 AND business_date BETWEEN p_from AND p_to;
END;

-- Bills with money still due, any day.
CREATE PROCEDURE sp_report_unpaid(IN p_business_id BIGINT)
BEGIN
  SELECT * FROM v_order_list
  WHERE business_id = p_business_id AND is_active = 1 AND order_status IN ('BILLED', 'PART_PAID') AND balance_amount > 0
  ORDER BY balance_amount DESC;
END;
