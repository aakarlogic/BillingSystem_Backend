-- =============================================================================
--  पावती (Pavti) – master data
--  Run after schema.sql. Ids are looked up by code, so this file does not
--  depend on auto-increment numbers.
-- =============================================================================

-- ------------------------------------------------------------------ roles
INSERT INTO roles (role_code, role_name, role_name_mr, role_name_hi, description, is_platform_role, sort_order) VALUES
('SUPER_ADMIN', 'Platform Admin', 'प्लॅटफॉर्म ॲडमिन', 'प्लेटफ़ॉर्म एडमिन', 'Runs the platform: businesses, plans and all users', 1, 1),
('OWNER',       'Owner',          'मालक',            'मालिक',           'Owns one business. Can do everything there, including billing', 0, 2),
('STAFF',       'Staff',          'स्टाफ',            'स्टाफ़',            'Waiter / cashier. Can do only what the owner ticks', 0, 3);

-- ------------------------------------------------------------ permissions
INSERT INTO permissions (permission_code, permission_name, permission_name_mr, permission_name_hi, description, default_for_staff, sort_order) VALUES
('ORDERS_TAKE',    'Take orders',            'ऑर्डर घेणे',          'ऑर्डर लेना',          'Start bills, add dishes, use tables and customers', 1, 1),
('PAYMENTS_TAKE',  'Take payments (cashier)', 'पैसे घेणे (कॅशियर)',   'पैसे लेना (कैशियर)',   'Mark bills paid by cash or UPI', 0, 2),
('DISCOUNTS_GIVE', 'Give discounts',         'सूट देणे',            'छूट देना',            'Discount on a bill, saved with a reason', 0, 3),
('BILLS_CANCEL',   'Cancel bills',           'बिल रद्द करणे',        'बिल रद्द करना',        'Cancel an unpaid bill with a reason', 0, 4),
('SALES_VIEW',     'See sales and reports',  'विक्री पाहणे',          'बिक्री देखना',         'Full home screen numbers and reports', 0, 5);

-- ------------------------------------------------------------------ pages
INSERT INTO pages (page_code, page_name, page_name_mr, page_name_hi, route_path, menu_group, icon_name, required_permission_code, show_in_menu, sort_order) VALUES
-- platform admin only
('BUSINESSES',  'Businesses',        'व्यवसाय',           'व्यवसाय',            '/platform/businesses', 'PLATFORM', 'building', NULL,             1, 10),
('PLANS',       'Plans',             'प्लॅन',              'प्लान',               '/platform/plans',      'PLATFORM', 'layers',   NULL,             1, 11),
('ALL_USERS',   'All users',         'सर्व युजर',          'सभी यूज़र',            '/platform/users',      'PLATFORM', 'users',    NULL,             1, 12),
-- daily billing
('HOME',        'Home',              'होम',               'होम',                '/dashboard',           'BILLING',  'dashboard', NULL,            1, 20),
('NEW_BILL',    'New bill',          'नवीन बिल',          'नया बिल',             '/billing/new',        'BILLING',  'plus',     'ORDERS_TAKE',    1, 21),
('TABLES',      'Tables',            'टेबल',              'टेबल',               '/tables',              'BILLING',  'table',    NULL,             1, 22),
('BILLS',       'Bills',             'बिले',              'बिल',                '/billing/bills',            'BILLING',  'invoice',  NULL,             1, 23),
('PAYMENTS',    'Payments',          'पेमेंट',             'भुगतान',              '/payments',            'BILLING',  'card',     'PAYMENTS_TAKE',  1, 24),
-- owner setup and reports
('MENU',        'Menu & products',   'मेन्यू आणि प्रॉडक्ट', 'मेन्यू और प्रोडक्ट',     '/menu',               'MANAGE',   'box',      NULL,             1, 30),
('CATEGORIES',  'Menu categories',   'मेन्यू गट',          'मेन्यू समूह',           '/menu/categories',    'MANAGE',   'box',      NULL,             0, 31),
('CUSTOMERS',   'Customers',         'ग्राहक',             'ग्राहक',              '/customers',           'MANAGE',   'contact',  NULL,             1, 32),
('REPORTS',     'Reports',           'रिपोर्ट',            'रिपोर्ट',              '/reports',             'MANAGE',   'chart',    'SALES_VIEW',     1, 33),
('DAY_CLOSE',   'Close day',         'दिवस बंद',          'दिन बंद',             '/day-close',           'MANAGE',   'clock',    NULL,             0, 34),
('TEAM',        'Team',              'टीम',               'टीम',                '/user-management/team',                'MANAGE',   'users',    NULL,             1, 35),
('SETTINGS',    'Settings',          'सेटिंग्ज',            'सेटिंग्स',             '/settings',            'MANAGE',   'settings', NULL,             1, 36),
('AUDIT_LOG',   'History (audit log)', 'इतिहास',           'इतिहास',             '/audit',               'MANAGE',   'log',      NULL,             1, 37),
-- everyone
('PROFILE',     'My profile',        'माझी प्रोफाइल',       'मेरी प्रोफ़ाइल',         '/profile',             'ACCOUNT',  'user',     NULL,             1, 90),
('GUIDE',       'Help guide',        'मार्गदर्शक',          'गाइड',               '/guide',               'ACCOUNT',  'help',     NULL,             1, 91);

-- ------------------------------------------------------- role page rights
-- v = view, a = add, e = edit, d = delete, x = export
INSERT INTO role_page_rights (role_id, page_id, can_view, can_add, can_edit, can_delete, can_export)
SELECT r.role_id, p.page_id, x.v, x.a, x.e, x.d, x.x
FROM (
            SELECT 'SUPER_ADMIN' AS rc, 'HOME'       AS pc, 1 AS v, 0 AS a, 0 AS e, 0 AS d, 0 AS x
  UNION ALL SELECT 'SUPER_ADMIN', 'BUSINESSES', 1, 1, 1, 1, 1
  UNION ALL SELECT 'SUPER_ADMIN', 'PLANS',      1, 1, 1, 1, 0
  UNION ALL SELECT 'SUPER_ADMIN', 'ALL_USERS',  1, 1, 1, 1, 1
  UNION ALL SELECT 'SUPER_ADMIN', 'AUDIT_LOG',  1, 0, 0, 0, 1
  UNION ALL SELECT 'SUPER_ADMIN', 'PROFILE',    1, 0, 1, 0, 0
  UNION ALL SELECT 'SUPER_ADMIN', 'GUIDE',      1, 0, 0, 0, 0

  UNION ALL SELECT 'OWNER', 'HOME',       1, 0, 0, 0, 0
  UNION ALL SELECT 'OWNER', 'NEW_BILL',   1, 1, 0, 0, 0
  UNION ALL SELECT 'OWNER', 'TABLES',     1, 1, 1, 1, 0
  UNION ALL SELECT 'OWNER', 'BILLS',      1, 1, 1, 1, 1
  UNION ALL SELECT 'OWNER', 'PAYMENTS',   1, 1, 1, 0, 1
  UNION ALL SELECT 'OWNER', 'MENU',       1, 1, 1, 1, 1
  UNION ALL SELECT 'OWNER', 'CATEGORIES', 1, 1, 1, 1, 0
  UNION ALL SELECT 'OWNER', 'CUSTOMERS',  1, 1, 1, 1, 1
  UNION ALL SELECT 'OWNER', 'REPORTS',    1, 0, 0, 0, 1
  UNION ALL SELECT 'OWNER', 'DAY_CLOSE',  1, 1, 1, 0, 1
  UNION ALL SELECT 'OWNER', 'TEAM',       1, 1, 1, 1, 0
  UNION ALL SELECT 'OWNER', 'SETTINGS',   1, 0, 1, 0, 0
  UNION ALL SELECT 'OWNER', 'AUDIT_LOG',  1, 0, 0, 0, 1
  UNION ALL SELECT 'OWNER', 'PROFILE',    1, 0, 1, 0, 0
  UNION ALL SELECT 'OWNER', 'GUIDE',      1, 0, 0, 0, 0

  UNION ALL SELECT 'STAFF', 'HOME',       1, 0, 0, 0, 0
  UNION ALL SELECT 'STAFF', 'NEW_BILL',   1, 1, 0, 0, 0
  UNION ALL SELECT 'STAFF', 'TABLES',     1, 0, 0, 0, 0
  UNION ALL SELECT 'STAFF', 'BILLS',      1, 1, 1, 0, 0
  UNION ALL SELECT 'STAFF', 'PAYMENTS',   1, 1, 0, 0, 0
  UNION ALL SELECT 'STAFF', 'MENU',       1, 0, 0, 0, 0
  UNION ALL SELECT 'STAFF', 'CUSTOMERS',  1, 1, 1, 0, 0
  UNION ALL SELECT 'STAFF', 'REPORTS',    1, 0, 0, 0, 0
  UNION ALL SELECT 'STAFF', 'PROFILE',    1, 0, 1, 0, 0
  UNION ALL SELECT 'STAFF', 'GUIDE',      1, 0, 0, 0, 0
) x
JOIN roles r ON r.role_code = x.rc
JOIN pages p ON p.page_code = x.pc;

-- ------------------------------------------------------------------ plans
INSERT INTO plans (plan_code, plan_name, description, monthly_price, max_users, max_tables, sort_order) VALUES
('FREE',    'Free',    'Owner only, for trying Pavti', 0.00,    1,  5,    1),
('STARTER', 'Starter', 'Owner + 3 staff',              499.00,  4,  15,   2),
('GROWTH',  'Growth',  'Owner + 10 staff',             999.00,  11, NULL, 3);

-- The first platform admin is created at start-up by AdminBootstrap.java
-- (password from the PAVTI_ADMIN_PASSWORD setting, stored as a BCrypt hash).
