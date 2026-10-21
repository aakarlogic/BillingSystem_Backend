-- Flyway runs this before every migration. Stored procedure parameters take the
-- database's default collation, so it must match the tables (utf8mb4_unicode_ci),
-- even when the database was created automatically with the server default.
ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
