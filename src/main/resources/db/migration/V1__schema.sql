-- =============================================================================
--  पावती (Pavti) – Restaurant & small-business billing
--  Database schema (MySQL 8)
--
--  Rules used in every table
--  • No FOREIGN KEY constraints. Columns ending in _id hold the id of another
--    table; the API checks them before saving. Indexes are added for speed.
--  • Every table ends with the same 5 audit columns:
--      is_active      1 = in use, 0 = deleted/disabled (we never hard-delete)
--      inserted_by    users.user_id who created the row (NULL = system)
--      inserted_date  when the row was created
--      updated_by     users.user_id who last changed the row
--      updated_date   when the row was last changed
--  • Every business-owned table has business_id, and every query must filter
--    by it, so one restaurant can never see another's data.
--  • Money is DECIMAL(12,2). Codes (status, type) are short VARCHARs in CAPS.
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. PLATFORM: plans and businesses
-- -----------------------------------------------------------------------------

-- Subscription plans that the platform sells to businesses.
CREATE TABLE plans (
  plan_id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_code        VARCHAR(30)   NOT NULL,            -- FREE, STARTER, GROWTH
  plan_name        VARCHAR(100)  NOT NULL,
  description      VARCHAR(255)  NULL,
  monthly_price    DECIMAL(12,2) NOT NULL DEFAULT 0,
  max_users        INT           NOT NULL DEFAULT 1,  -- owner + staff seats
  max_tables       INT           NULL,                -- NULL = unlimited
  sort_order       INT           NOT NULL DEFAULT 0,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_plans_code UNIQUE (plan_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per restaurant / shop (the "tenant"). Bill settings live here too.
CREATE TABLE businesses (
  business_id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_code       VARCHAR(30)   NOT NULL,          -- short unique code, e.g. SPICE01
  business_name       VARCHAR(150)  NOT NULL,
  business_type       VARCHAR(20)   NOT NULL DEFAULT 'RESTAURANT', -- RESTAURANT, CAFE, SHOP, SERVICE
  owner_name          VARCHAR(100)  NULL,
  mobile_no           VARCHAR(15)   NULL,
  email               VARCHAR(150)  NULL,
  address_line        VARCHAR(255)  NULL,
  city                VARCHAR(80)   NULL,
  state               VARCHAR(80)   NULL,
  pincode             VARCHAR(10)   NULL,
  gstin               VARCHAR(20)   NULL,
  logo_url            VARCHAR(500)  NULL,
  -- bill settings
  currency_code       VARCHAR(3)    NOT NULL DEFAULT 'INR',
  default_language    VARCHAR(5)    NOT NULL DEFAULT 'mr',  -- mr, hi, en
  bill_prefix         VARCHAR(10)   NOT NULL DEFAULT 'BILL-',
  next_bill_no        INT           NOT NULL DEFAULT 1,
  default_tax_rate    DECIMAL(5,2)  NOT NULL DEFAULT 5.00,
  service_charge_pct  DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
  service_charge_label VARCHAR(50)  NOT NULL DEFAULT 'Service charge',
  bill_footer_note    VARCHAR(255)  NULL,
  day_close_time      TIME          NOT NULL DEFAULT '04:00:00', -- business day ends here
  time_zone           VARCHAR(40)   NOT NULL DEFAULT 'Asia/Kolkata',
  -- subscription
  plan_id             BIGINT        NULL,              -- plans.plan_id
  plan_start_date     DATE          NULL,
  plan_end_date       DATE          NULL,
  status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING, ACTIVE, SUSPENDED
  status_reason       VARCHAR(255)  NULL,

  is_active           TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by         BIGINT        NULL,
  inserted_date       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by          BIGINT        NULL,
  updated_date        DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_businesses_code UNIQUE (business_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_businesses_plan ON businesses (plan_id);
CREATE INDEX ix_businesses_mobile ON businesses (mobile_no);

-- History of plan changes and subscription payments to the platform.
CREATE TABLE business_subscriptions (
  subscription_id  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  plan_id          BIGINT        NOT NULL,            -- plans.plan_id
  start_date       DATE          NOT NULL,
  end_date         DATE          NOT NULL,
  amount           DECIMAL(12,2) NOT NULL DEFAULT 0,
  payment_status   VARCHAR(20)   NOT NULL DEFAULT 'UNPAID', -- UNPAID, PAID, WAIVED
  paid_date        DATETIME      NULL,
  remarks          VARCHAR(255)  NULL,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_subscriptions_business ON business_subscriptions (business_id);

-- -----------------------------------------------------------------------------
-- 2. ACCESS: roles, pages, page rights, permissions
-- -----------------------------------------------------------------------------

-- Role master: SUPER_ADMIN (platform), OWNER, STAFF.
CREATE TABLE roles (
  role_id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_code        VARCHAR(30)   NOT NULL,
  role_name        VARCHAR(100)  NOT NULL,
  role_name_mr     VARCHAR(100)  NULL,
  role_name_hi     VARCHAR(100)  NULL,
  description      VARCHAR(255)  NULL,
  is_platform_role TINYINT(1)    NOT NULL DEFAULT 0,  -- 1 = not tied to a business
  sort_order       INT           NOT NULL DEFAULT 0,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_roles_code UNIQUE (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every screen in the app. The menu is built from this table.
CREATE TABLE pages (
  page_id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  page_code        VARCHAR(50)   NOT NULL,            -- HOME, NEW_BILL, TABLES ...
  page_name        VARCHAR(100)  NOT NULL,
  page_name_mr     VARCHAR(100)  NULL,
  page_name_hi     VARCHAR(100)  NULL,
  route_path       VARCHAR(150)  NOT NULL,            -- /tables
  menu_group       VARCHAR(30)   NOT NULL,            -- PLATFORM, BILLING, MANAGE, ACCOUNT
  icon_name        VARCHAR(50)   NULL,
  parent_page_id   BIGINT        NULL,                -- pages.page_id, for sub pages
  required_permission_code VARCHAR(50) NULL,          -- permissions.permission_code a STAFF user must also have
  show_in_menu     TINYINT(1)    NOT NULL DEFAULT 1,
  sort_order       INT           NOT NULL DEFAULT 0,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_pages_code UNIQUE (page_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Which role can open which page, and what it can do there.
CREATE TABLE role_page_rights (
  role_page_right_id BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_id          BIGINT        NOT NULL,            -- roles.role_id
  page_id          BIGINT        NOT NULL,            -- pages.page_id
  can_view         TINYINT(1)    NOT NULL DEFAULT 0,
  can_add          TINYINT(1)    NOT NULL DEFAULT 0,
  can_edit         TINYINT(1)    NOT NULL DEFAULT 0,
  can_delete       TINYINT(1)    NOT NULL DEFAULT 0,
  can_export       TINYINT(1)    NOT NULL DEFAULT 0,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_role_page UNIQUE (role_id, page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Small tick-box permissions the owner gives each staff member
-- (take orders, take payments, give discounts, cancel bills, see sales).
CREATE TABLE permissions (
  permission_id    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  permission_code  VARCHAR(50)   NOT NULL,            -- ORDERS_TAKE, PAYMENTS_TAKE ...
  permission_name  VARCHAR(100)  NOT NULL,
  permission_name_mr VARCHAR(100) NULL,
  permission_name_hi VARCHAR(100) NULL,
  description      VARCHAR(255)  NULL,
  default_for_staff TINYINT(1)   NOT NULL DEFAULT 0,  -- ticked by default for new staff
  sort_order       INT           NOT NULL DEFAULT 0,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_permissions_code UNIQUE (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. USERS: register, login, sessions
-- -----------------------------------------------------------------------------

-- Every person who can sign in. business_id is NULL for the platform admin.
CREATE TABLE users (
  user_id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NULL,                -- businesses.business_id
  role_id          BIGINT        NOT NULL,            -- roles.role_id
  full_name        VARCHAR(100)  NOT NULL,
  mobile_no        VARCHAR(15)   NOT NULL,            -- main login id for small businesses
  email            VARCHAR(150)  NULL,
  password_hash    VARCHAR(255)  NOT NULL,            -- bcrypt/argon2, never plain text
  login_pin_hash   VARCHAR(255)  NULL,                -- optional 4-digit PIN for staff on a shared tablet
  preferred_language VARCHAR(5)  NOT NULL DEFAULT 'mr',
  profile_photo_url VARCHAR(500) NULL,
  is_mobile_verified TINYINT(1)  NOT NULL DEFAULT 0,
  last_login_date  DATETIME      NULL,
  failed_login_count INT         NOT NULL DEFAULT 0,
  locked_until     DATETIME      NULL,                -- set after too many wrong passwords
  must_change_password TINYINT(1) NOT NULL DEFAULT 0, -- 1 when owner created the account

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_users_mobile UNIQUE (mobile_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_users_business ON users (business_id);
CREATE INDEX ix_users_email ON users (email);

-- Staff tick-box permissions (one row per staff member per permission).
CREATE TABLE user_permissions (
  user_permission_id BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  user_id          BIGINT        NOT NULL,            -- users.user_id
  permission_id    BIGINT        NOT NULL,            -- permissions.permission_id
  is_allowed       TINYINT(1)    NOT NULL DEFAULT 1,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_user_permission UNIQUE (user_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_user_permissions_business ON user_permissions (business_id);

-- One-time codes for mobile verification at sign-up and for "forgot password".
CREATE TABLE user_otps (
  otp_id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  mobile_no        VARCHAR(15)   NOT NULL,
  user_id          BIGINT        NULL,                -- users.user_id (NULL during sign-up)
  purpose          VARCHAR(20)   NOT NULL,            -- REGISTER, RESET_PASSWORD, LOGIN
  otp_hash         VARCHAR(255)  NOT NULL,
  expires_at       DATETIME      NOT NULL,
  attempts         INT           NOT NULL DEFAULT 0,
  used_date        DATETIME      NULL,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_user_otps_mobile ON user_otps (mobile_no, purpose);

-- Logged-in devices. Deleting (is_active = 0) a row signs that device out.
CREATE TABLE user_sessions (
  session_id       BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT        NOT NULL,            -- users.user_id
  business_id      BIGINT        NULL,                -- businesses.business_id
  token_hash       VARCHAR(255)  NOT NULL,            -- hash of the refresh token
  device_name      VARCHAR(150)  NULL,
  ip_address       VARCHAR(45)   NULL,
  login_date       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_date   DATETIME      NULL,
  expires_at       DATETIME      NOT NULL,
  logout_date      DATETIME      NULL,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_sessions_token UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_sessions_user ON user_sessions (user_id);

-- Every sign-in attempt, successful or not (security history).
CREATE TABLE login_history (
  login_history_id BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT        NULL,                -- users.user_id (NULL if unknown login id)
  login_id_entered VARCHAR(150)  NOT NULL,            -- mobile/email typed
  is_success       TINYINT(1)    NOT NULL DEFAULT 0,
  failure_reason   VARCHAR(100)  NULL,                -- WRONG_PASSWORD, LOCKED, BUSINESS_SUSPENDED
  ip_address       VARCHAR(45)   NULL,
  device_name      VARCHAR(150)  NULL,
  attempt_date     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_login_history_user ON login_history (user_id, attempt_date);

-- -----------------------------------------------------------------------------
-- 4. RESTAURANT SETUP: tables, menu categories, menu items, customers
-- -----------------------------------------------------------------------------

-- Dining tables (Table 1, Table 2 …). Status changes as orders move.
CREATE TABLE dining_tables (
  table_id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  table_name       VARCHAR(50)   NOT NULL,            -- "Table 4", "Garden 2"
  area_name        VARCHAR(50)   NULL,                -- Indoor, Garden, AC hall
  seats            INT           NOT NULL DEFAULT 4,
  table_status     VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, OCCUPIED, BILL_PENDING
  current_order_id BIGINT        NULL,                -- orders.order_id while occupied
  sort_order       INT           NOT NULL DEFAULT 0,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_tables_name UNIQUE (business_id, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Menu groups: Starters, South Indian, Drinks …
CREATE TABLE menu_categories (
  category_id      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  category_name    VARCHAR(100)  NOT NULL,
  category_name_mr VARCHAR(100)  NULL,
  category_name_hi VARCHAR(100)  NULL,
  sort_order       INT           NOT NULL DEFAULT 0,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_categories_name UNIQUE (business_id, category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dishes / products. This is where the owner "adds a dish".
CREATE TABLE menu_items (
  item_id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  category_id      BIGINT        NULL,                -- menu_categories.category_id
  item_code        VARCHAR(30)   NULL,                -- short code / SKU
  item_name        VARCHAR(150)  NOT NULL,
  item_name_mr     VARCHAR(150)  NULL,
  item_name_hi     VARCHAR(150)  NULL,
  description      VARCHAR(255)  NULL,
  image_url        VARCHAR(500)  NULL,
  unit_name        VARCHAR(20)   NOT NULL DEFAULT 'plate', -- plate, cup, pc, kg
  price            DECIMAL(12,2) NOT NULL DEFAULT 0,
  tax_rate         DECIMAL(5,2)  NOT NULL DEFAULT 5.00,
  is_veg           TINYINT(1)    NOT NULL DEFAULT 1,
  is_available     TINYINT(1)    NOT NULL DEFAULT 1,  -- 0 = "sold out" today, still on menu
  sort_order       INT           NOT NULL DEFAULT 0,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_items_business ON menu_items (business_id, category_id);
CREATE INDEX ix_items_code ON menu_items (business_id, item_code);

-- Every price change of a dish (old bills keep their own price anyway).
CREATE TABLE menu_item_price_history (
  price_history_id BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  item_id          BIGINT        NOT NULL,            -- menu_items.item_id
  old_price        DECIMAL(12,2) NOT NULL,
  new_price        DECIMAL(12,2) NOT NULL,
  old_tax_rate     DECIMAL(5,2)  NULL,
  new_tax_rate     DECIMAL(5,2)  NULL,
  changed_date     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_price_history_item ON menu_item_price_history (item_id);

-- Customers (optional for restaurants; useful for regulars and credit).
CREATE TABLE customers (
  customer_id      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  customer_name    VARCHAR(150)  NOT NULL,
  mobile_no        VARCHAR(15)   NULL,
  email            VARCHAR(150)  NULL,
  address_line     VARCHAR(255)  NULL,
  gstin            VARCHAR(20)   NULL,
  notes            VARCHAR(255)  NULL,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_customers_business ON customers (business_id, mobile_no);

-- -----------------------------------------------------------------------------
-- 5. BILLING: orders (bills), order items, payments
-- -----------------------------------------------------------------------------

-- One order = one bill. Flow: OPEN -> BILLED -> PAID (or CANCELLED).
CREATE TABLE orders (
  order_id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id        BIGINT        NOT NULL,          -- businesses.business_id
  bill_no            VARCHAR(30)   NULL,              -- given when the bill is made, e.g. SGC-00062
  order_type         VARCHAR(20)   NOT NULL DEFAULT 'DINE_IN', -- DINE_IN, TAKEAWAY, COUNTER
  table_id           BIGINT        NULL,              -- dining_tables.table_id
  customer_id        BIGINT        NULL,              -- customers.customer_id
  customer_name      VARCHAR(150)  NULL,              -- name/table printed on bill (snapshot)
  guest_count        INT           NULL,
  order_status       VARCHAR(20)   NOT NULL DEFAULT 'OPEN',   -- OPEN, BILLED, PART_PAID, PAID, CANCELLED, REFUNDED
  business_date      DATE          NOT NULL,          -- report day (uses day_close_time)
  -- amounts (all calculated by the API, stored for fast reports)
  sub_total          DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_type      VARCHAR(10)   NOT NULL DEFAULT 'NONE',   -- NONE, PERCENT, AMOUNT
  discount_value     DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_amount    DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_reason    VARCHAR(255)  NULL,
  taxable_amount     DECIMAL(12,2) NOT NULL DEFAULT 0,
  tax_amount         DECIMAL(12,2) NOT NULL DEFAULT 0,
  service_charge_pct DECIMAL(5,2)  NOT NULL DEFAULT 0,
  service_charge_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  round_off          DECIMAL(6,2)  NOT NULL DEFAULT 0,
  total_amount       DECIMAL(12,2) NOT NULL DEFAULT 0,
  paid_amount        DECIMAL(12,2) NOT NULL DEFAULT 0,
  balance_amount     DECIMAL(12,2) NOT NULL DEFAULT 0,
  notes              VARCHAR(255)  NULL,
  -- who/when for each step
  waiter_user_id     BIGINT        NULL,              -- users.user_id who took the order
  billed_by          BIGINT        NULL,              -- users.user_id who made the bill
  billed_date        DATETIME      NULL,
  paid_date          DATETIME      NULL,
  cancelled_by       BIGINT        NULL,              -- users.user_id
  cancelled_date     DATETIME      NULL,
  cancel_reason      VARCHAR(255)  NULL,

  is_active          TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by        BIGINT        NULL,
  inserted_date      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         BIGINT        NULL,
  updated_date       DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_orders_bill_no UNIQUE (business_id, bill_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_orders_day ON orders (business_id, business_date, order_status);
CREATE INDEX ix_orders_table ON orders (business_id, table_id, order_status);

-- Dishes on an order. Name, price and tax are copied at the time of sale,
-- so changing the menu later never changes an old bill.
CREATE TABLE order_items (
  order_item_id    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  order_id         BIGINT        NOT NULL,            -- orders.order_id
  item_id          BIGINT        NULL,                -- menu_items.item_id (NULL = custom line)
  item_name        VARCHAR(150)  NOT NULL,            -- snapshot
  unit_name        VARCHAR(20)   NULL,
  quantity         DECIMAL(10,3) NOT NULL DEFAULT 1,
  unit_price       DECIMAL(12,2) NOT NULL,            -- snapshot
  line_amount      DECIMAL(12,2) NOT NULL,            -- quantity × unit_price
  discount_amount  DECIMAL(12,2) NOT NULL DEFAULT 0,  -- share of bill discount
  tax_rate         DECIMAL(5,2)  NOT NULL DEFAULT 0,  -- snapshot
  tax_amount       DECIMAL(12,2) NOT NULL DEFAULT 0,
  line_total       DECIMAL(12,2) NOT NULL,
  kitchen_note     VARCHAR(150)  NULL,                -- "less spicy"
  kot_status       VARCHAR(20)   NOT NULL DEFAULT 'NEW', -- NEW, SENT, READY, SERVED
  added_by         BIGINT        NULL,                -- users.user_id

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_item ON order_items (business_id, item_id);

-- Money received for a bill (customer paid by cash or UPI scanner outside the
-- app; staff only marks it here). Refunds are rows with payment_type = REFUND.
CREATE TABLE payments (
  payment_id       BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  order_id         BIGINT        NOT NULL,            -- orders.order_id
  payment_type     VARCHAR(10)   NOT NULL DEFAULT 'PAYMENT', -- PAYMENT, REFUND
  payment_mode     VARCHAR(10)   NOT NULL,            -- CASH, UPI
  amount           DECIMAL(12,2) NOT NULL,
  cash_received    DECIMAL(12,2) NULL,                -- note given by customer
  change_returned  DECIMAL(12,2) NULL,
  reference_no     VARCHAR(50)   NULL,                -- UPI ref (optional)
  refund_of_payment_id BIGINT    NULL,                -- payments.payment_id
  reason           VARCHAR(255)  NULL,                -- required for refunds
  business_date    DATE          NOT NULL,
  request_key      VARCHAR(64)   NOT NULL,            -- blocks double tap / duplicate save
  received_by      BIGINT        NULL,                -- users.user_id
  received_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_payments_request UNIQUE (business_id, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_payments_order ON payments (order_id);
CREATE INDEX ix_payments_day ON payments (business_id, business_date, payment_mode);

-- End-of-day cash check: expected vs counted.
CREATE TABLE day_closings (
  day_closing_id   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NOT NULL,            -- businesses.business_id
  business_date    DATE          NOT NULL,
  bill_count       INT           NOT NULL DEFAULT 0,
  total_sales      DECIMAL(12,2) NOT NULL DEFAULT 0,
  cash_expected    DECIMAL(12,2) NOT NULL DEFAULT 0,
  cash_counted     DECIMAL(12,2) NOT NULL DEFAULT 0,
  cash_difference  DECIMAL(12,2) NOT NULL DEFAULT 0,
  upi_total        DECIMAL(12,2) NOT NULL DEFAULT 0,
  unpaid_total     DECIMAL(12,2) NOT NULL DEFAULT 0,
  cancelled_count  INT           NOT NULL DEFAULT 0,
  remarks          VARCHAR(255)  NULL,
  closed_by        BIGINT        NULL,                -- users.user_id
  closed_date      DATETIME      NULL,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_day_closing UNIQUE (business_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 6. AUDIT: who did what, when and why
-- -----------------------------------------------------------------------------

CREATE TABLE audit_logs (
  audit_log_id     BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id      BIGINT        NULL,                -- NULL = platform action
  user_id          BIGINT        NULL,                -- users.user_id
  action_code      VARCHAR(50)   NOT NULL,            -- BILL_CANCEL, DISCOUNT_GIVEN, PRICE_CHANGE, LOGIN ...
  entity_name      VARCHAR(50)   NOT NULL,            -- orders, menu_items, users ...
  entity_id        BIGINT        NULL,
  old_value        TEXT          NULL,                -- JSON of changed fields before
  new_value        TEXT          NULL,                -- JSON after
  reason           VARCHAR(255)  NULL,
  ip_address       VARCHAR(45)   NULL,
  action_date      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_audit_business ON audit_logs (business_id, action_date);
CREATE INDEX ix_audit_entity ON audit_logs (entity_name, entity_id);

-- -----------------------------------------------------------------------------
-- 7. TECHNICAL LOGS (same two logs as Health 360: errors + API requests)
-- -----------------------------------------------------------------------------

-- Every unexpected error (written by LoggerDal.storeErrorLog).
CREATE TABLE error_logs (
  error_log_id     BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  class_name       VARCHAR(100)  NOT NULL,
  method_name      VARCHAR(100)  NOT NULL,
  message          VARCHAR(500)  NULL,
  error_description TEXT         NULL,
  stack_trace      TEXT          NULL,
  user_id          BIGINT        NULL,               -- users.user_id

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_error_logs_date ON error_logs (inserted_date);

-- Every write request (POST) with the status it got. Passwords/OTPs are masked.
CREATE TABLE api_request_logs (
  api_request_log_id BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  api_url          VARCHAR(200)  NOT NULL,
  request_json     TEXT          NULL,
  status_code      INT           NULL,
  duration_ms      INT           NULL,
  user_id          BIGINT        NULL,               -- users.user_id
  business_id      BIGINT        NULL,               -- businesses.business_id
  ip_address       VARCHAR(45)   NULL,

  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  inserted_by      BIGINT        NULL,
  inserted_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_date     DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX ix_api_logs_date ON api_request_logs (inserted_date);
