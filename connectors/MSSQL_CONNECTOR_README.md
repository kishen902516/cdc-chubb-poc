# SQL Server (MSSQL) CDC Connector

This document provides instructions for setting up and using the SQL Server CDC connector with the Debezium CDC Application.

## Prerequisites

1. SQL Server 2016 or later (2022 recommended)
2. SQL Server Agent must be running (required for CDC)
3. Database user with appropriate permissions (db_owner or specific CDC permissions)
4. CDC must be enabled at both database and table levels

## Quick Start

### 1. Start SQL Server using Docker Compose

```bash
# Start all services including SQL Server
docker-compose up -d sqlserver kafka zookeeper

# Wait for SQL Server to be ready (check health)
docker-compose ps
```

### 2. Enable CDC on SQL Server

Run the setup script to enable CDC and create sample tables:

```bash
# Using docker exec
docker exec -i cdc-sqlserver /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U sa -P "SqlServer2022!" \
  -i /scripts/setup-sqlserver-cdc.sql

# Or manually connect and run the script
docker exec -it cdc-sqlserver /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U sa -P "SqlServer2022!"
```

### 3. Configure the Application

Use the provided SQL Server configuration file:

```bash
# Copy the SQL Server config as the active configuration
cp src/main/resources/cdc-config-sqlserver.yml src/main/resources/cdc-config.yml

# Or set the configuration path environment variable
export CDC_CONFIG_PATH=src/main/resources/cdc-config-sqlserver.yml
```

### 4. Build and Run the Application

```bash
# Build the application
mvn clean package

# Run with SQL Server configuration
java -jar target/debezium-cdc-app-1.0.0-SNAPSHOT.jar \
  --spring.config.location=classpath:cdc-config-sqlserver.yml
```

## Configuration Details

### Database Configuration

```yaml
database:
  type: SQLSERVER
  host: localhost
  port: 1433
  database: cdcdb
  username: sa
  password: ${DB_PASSWORD:SqlServer2022!}
```

### Enabling CDC on Tables

CDC must be enabled at both database and table levels:

```sql
-- Enable CDC on database
EXEC sys.sp_cdc_enable_db;

-- Enable CDC on a specific table
EXEC sys.sp_cdc_enable_table
    @source_schema = N'dbo',
    @source_name = N'your_table_name',
    @role_name = NULL,
    @supports_net_changes = 1;
```

### Tables Configuration

```yaml
tables:
  - name: dbo.orders
    includeMode: INCLUDE_ALL

  - name: dbo.customers
    includeMode: EXCLUDE_SPECIFIED
    columnFilter:
      - password_hash
      - ssn
      - credit_card
```

## Testing the Connector

### Insert Test Data

```sql
-- Connect to SQL Server
docker exec -it cdc-sqlserver /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U sa -P "SqlServer2022!"

-- Insert test data
USE cdcdb;
GO

INSERT INTO orders (customer_id, status, total_amount, shipping_address)
VALUES (1, 'NEW', 99.99, '123 Test St');
GO
```

### Update Test Data

```sql
UPDATE orders
SET status = 'PROCESSING', updated_at = GETUTCDATE()
WHERE order_id = 1;
GO
```

### Delete Test Data

```sql
DELETE FROM orders WHERE order_id = 1;
GO
```

### Verify Events in Kafka

```bash
# List topics
docker exec -it cdc-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Consume events from orders topic
docker exec -it cdc-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic cdc.sqlserver.cdcdb.dbo.orders \
  --from-beginning
```

Or use the Kafka UI at http://localhost:8090

## SQL Server Specific Considerations

### 1. SQL Server Agent

SQL Server Agent **MUST** be running for CDC to capture changes. The Docker image is configured with `MSSQL_AGENT_ENABLED=true`.

### 2. CDC Latency

SQL Server CDC uses SQL Server Agent jobs to capture changes. There may be a slight delay (typically 1-5 seconds) between when a change occurs and when it's available for capture.

### 3. Schema Changes

When table schema changes:
1. CDC capture will continue but may not capture new columns
2. To capture new columns, disable and re-enable CDC on the table:

```sql
-- Disable CDC
EXEC sys.sp_cdc_disable_table
    @source_schema = N'dbo',
    @source_name = N'table_name',
    @capture_instance = N'all';

-- Re-enable CDC
EXEC sys.sp_cdc_enable_table
    @source_schema = N'dbo',
    @source_name = N'table_name',
    @role_name = NULL,
    @supports_net_changes = 1;
```

### 4. Permissions

The database user needs:
- `db_owner` role (simplest for development)
- OR specific CDC permissions:
  - `db_datareader`
  - `db_datawriter`
  - `db_ddladmin`
  - Execute permissions on CDC stored procedures

### 5. Transaction Log Management

CDC reads from the transaction log. Ensure:
- Transaction log has sufficient space
- Regular log backups are performed in production
- Monitor log growth when CDC is enabled

## Troubleshooting

### Check CDC Status

```sql
-- Check if database has CDC enabled
SELECT name, is_cdc_enabled
FROM sys.databases
WHERE name = 'cdcdb';

-- Check which tables have CDC enabled
SELECT name, is_tracked_by_cdc
FROM sys.tables
WHERE is_tracked_by_cdc = 1;

-- View CDC configuration
SELECT * FROM cdc.change_tables;

-- Check CDC job status
EXEC sys.sp_cdc_help_jobs;
```

### Common Issues

1. **CDC not capturing changes**
   - Ensure SQL Server Agent is running
   - Check CDC job status
   - Verify table has CDC enabled

2. **Connection errors**
   - Verify SQL Server is accepting TCP/IP connections
   - Check firewall/port settings (1433)
   - Ensure correct username/password

3. **Permission errors**
   - Grant appropriate permissions to the CDC user
   - Use `db_owner` role for development

## Integration Test

Run the SQL Server CDC integration test:

```bash
mvn test -Dtest=SqlServerCdcIntegrationTest
```

Note: Requires Docker to be running.

## Production Considerations

1. Use a dedicated CDC user with minimal required permissions
2. Monitor transaction log growth
3. Set up appropriate log backup schedule
4. Consider CDC cleanup job settings
5. Use SSL/TLS for database connections
6. Monitor CDC latency and performance
7. Plan for schema evolution strategy

## References

- [Debezium SQL Server Connector Documentation](https://debezium.io/documentation/reference/stable/connectors/sqlserver.html)
- [SQL Server CDC Documentation](https://docs.microsoft.com/en-us/sql/relational-databases/track-changes/about-change-data-capture-sql-server)
- [SQL Server Agent Documentation](https://docs.microsoft.com/en-us/sql/ssms/agent/sql-server-agent)