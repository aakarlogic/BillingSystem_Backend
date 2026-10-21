-- Small additions after V1-V6:
--  * best sellers for one person (staff without SALES_VIEW see only their own work)

DROP PROCEDURE IF EXISTS sp_report_items;

CREATE PROCEDURE sp_report_items(IN p_business_id BIGINT, IN p_from DATE, IN p_to DATE, IN p_mine_user_id BIGINT)
BEGIN
  SELECT oi.item_name, SUM(oi.quantity) AS quantity, SUM(oi.line_amount - oi.discount_amount) AS sales
  FROM order_items oi
  JOIN orders o ON o.order_id = oi.order_id
  WHERE o.business_id = p_business_id AND o.is_active = 1 AND oi.is_active = 1
    AND o.order_status IN ('BILLED', 'PART_PAID', 'PAID', 'REFUNDED') AND o.business_date BETWEEN p_from AND p_to
    AND (p_mine_user_id IS NULL OR o.waiter_user_id = p_mine_user_id OR o.billed_by = p_mine_user_id)
  GROUP BY oi.item_name;
END;

-- Row locks for money changes. Called first inside a Java @Transactional method, so two
-- cashiers can never both take the last balance, and two waiters never open one table.
CREATE PROCEDURE sp_order_lock(IN p_order_id BIGINT, IN p_business_id BIGINT)
BEGIN
  SELECT order_status FROM orders
  WHERE order_id = p_order_id AND business_id = p_business_id AND is_active = 1 FOR UPDATE;
END;

CREATE PROCEDURE sp_table_lock(IN p_table_id BIGINT, IN p_business_id BIGINT)
BEGIN
  SELECT table_id, business_id, table_name, table_status, current_order_id FROM dining_tables
  WHERE table_id = p_table_id AND business_id = p_business_id AND is_active = 1 FOR UPDATE;
END;

-- A removed group keeps its row (soft delete); rename it so the same name can be used again
-- (menu_categories has UNIQUE business_id + category_name).
DROP PROCEDURE IF EXISTS sp_category_delete;

CREATE PROCEDURE sp_category_delete(IN p_category_id BIGINT, IN p_business_id BIGINT, IN p_by BIGINT)
BEGIN
  IF EXISTS (SELECT 1 FROM menu_items WHERE category_id = p_category_id AND business_id = p_business_id AND is_active = 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Move or remove the dishes in this group first.';
  END IF;
  UPDATE menu_categories SET is_active = 0, category_name = LEFT(CONCAT(category_name, ' #', category_id), 100), updated_by = p_by
  WHERE category_id = p_category_id AND business_id = p_business_id AND is_active = 1;
END;
