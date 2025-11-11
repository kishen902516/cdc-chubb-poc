-- SQL Server CDC Setup Script
-- This script enables CDC on SQL Server and creates sample tables for testing

-- Enable CDC at database level (run as sa or db_owner)
USE master;
GO

-- Create database if it doesn't exist
IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'cdcdb')
BEGIN
    CREATE DATABASE cdcdb;
END
GO

-- Use the database
USE cdcdb;
GO

-- Enable CDC on the database
IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'cdcdb' AND is_cdc_enabled = 1)
BEGIN
    EXEC sys.sp_cdc_enable_db;
    PRINT 'CDC enabled on database cdcdb';
END
ELSE
BEGIN
    PRINT 'CDC already enabled on database cdcdb';
END
GO

-- Create sample tables
-- Orders table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'orders')
BEGIN
    CREATE TABLE orders (
        order_id INT IDENTITY(1,1) PRIMARY KEY,
        customer_id INT NOT NULL,
        order_date DATETIME2 DEFAULT GETUTCDATE(),
        status NVARCHAR(50) NOT NULL,
        total_amount DECIMAL(10, 2),
        shipping_address NVARCHAR(500),
        created_at DATETIME2 DEFAULT GETUTCDATE(),
        updated_at DATETIME2 DEFAULT GETUTCDATE()
    );
    PRINT 'Created orders table';
END
GO

-- Customers table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'customers')
BEGIN
    CREATE TABLE customers (
        customer_id INT IDENTITY(1,1) PRIMARY KEY,
        first_name NVARCHAR(100) NOT NULL,
        last_name NVARCHAR(100) NOT NULL,
        email NVARCHAR(255) NOT NULL UNIQUE,
        phone NVARCHAR(50),
        address NVARCHAR(500),
        city NVARCHAR(100),
        country NVARCHAR(100),
        password_hash NVARCHAR(500),  -- Sensitive - should be excluded in CDC
        ssn NVARCHAR(20),              -- Sensitive - should be excluded in CDC
        credit_card NVARCHAR(100),     -- Sensitive - should be excluded in CDC
        created_at DATETIME2 DEFAULT GETUTCDATE(),
        updated_at DATETIME2 DEFAULT GETUTCDATE()
    );
    PRINT 'Created customers table';
END
GO

-- Products table (example with composite key scenario)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'products')
BEGIN
    CREATE TABLE products (
        product_id INT IDENTITY(1,1) PRIMARY KEY,
        product_name NVARCHAR(200) NOT NULL,
        category NVARCHAR(100),
        price DECIMAL(10, 2),
        stock_quantity INT DEFAULT 0,
        created_at DATETIME2 DEFAULT GETUTCDATE(),
        updated_at DATETIME2 DEFAULT GETUTCDATE()
    );
    PRINT 'Created products table';
END
GO

-- Enable CDC on orders table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'orders' AND is_tracked_by_cdc = 1)
BEGIN
    EXEC sys.sp_cdc_enable_table
        @source_schema = N'dbo',
        @source_name = N'orders',
        @role_name = NULL,
        @supports_net_changes = 1;
    PRINT 'CDC enabled on orders table';
END
ELSE
BEGIN
    PRINT 'CDC already enabled on orders table';
END
GO

-- Enable CDC on customers table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'customers' AND is_tracked_by_cdc = 1)
BEGIN
    EXEC sys.sp_cdc_enable_table
        @source_schema = N'dbo',
        @source_name = N'customers',
        @role_name = NULL,
        @supports_net_changes = 1;
    PRINT 'CDC enabled on customers table';
END
ELSE
BEGIN
    PRINT 'CDC already enabled on customers table';
END
GO

-- Enable CDC on products table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'products' AND is_tracked_by_cdc = 1)
BEGIN
    EXEC sys.sp_cdc_enable_table
        @source_schema = N'dbo',
        @source_name = N'products',
        @role_name = NULL,
        @supports_net_changes = 1;
    PRINT 'CDC enabled on products table';
END
ELSE
BEGIN
    PRINT 'CDC already enabled on products table';
END
GO

-- Verify CDC is enabled
SELECT
    name AS TableName,
    is_tracked_by_cdc AS CDCEnabled
FROM sys.tables
WHERE name IN ('orders', 'customers', 'products');
GO

-- Insert sample data for testing
-- Sample customers
INSERT INTO customers (first_name, last_name, email, phone, address, city, country, password_hash, ssn, credit_card)
VALUES
    ('John', 'Doe', 'john.doe@example.com', '123-456-7890', '123 Main St', 'New York', 'USA', 'hash123', '123-45-6789', '1234-5678-9012-3456'),
    ('Jane', 'Smith', 'jane.smith@example.com', '098-765-4321', '456 Oak Ave', 'Los Angeles', 'USA', 'hash456', '987-65-4321', '9876-5432-1098-7654');

-- Sample products
INSERT INTO products (product_name, category, price, stock_quantity)
VALUES
    ('Laptop', 'Electronics', 999.99, 50),
    ('Mouse', 'Electronics', 29.99, 200),
    ('Keyboard', 'Electronics', 79.99, 150);

-- Sample orders
INSERT INTO orders (customer_id, status, total_amount, shipping_address)
VALUES
    (1, 'PENDING', 1029.98, '123 Main St, New York, USA'),
    (2, 'SHIPPED', 79.99, '456 Oak Ave, Los Angeles, USA');

PRINT 'Sample data inserted';
GO

-- Show CDC capture instances
SELECT
    capture_instance,
    source_schema,
    source_table,
    create_date
FROM cdc.change_tables;
GO

-- Check SQL Server Agent status (required for CDC)
-- Note: SQL Server Agent must be running for CDC to work properly
EXEC xp_servicecontrol 'QueryState', 'SQLSERVERAGENT';
GO

PRINT 'CDC setup complete!';
PRINT 'Make sure SQL Server Agent is running for CDC to capture changes.';
GO