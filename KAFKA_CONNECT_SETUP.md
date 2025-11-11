# Kafka Connect and Debezium Setup Guide

## Overview

This guide covers two approaches for setting up Change Data Capture (CDC) with Debezium:
1. **Embedded Debezium Engine** (Currently implemented in your application)
2. **Kafka Connect with Standalone Debezium Connectors** (Distributed architecture)

## Table of Contents
- [Quick Start](#quick-start)
- [Option 1: Using Your Embedded Debezium Engine](#option-1-using-your-embedded-debezium-engine)
- [Option 2: Setting Up Kafka Connect](#option-2-setting-up-kafka-connect)
- [PostgreSQL Configuration](#postgresql-configuration)
- [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)

## Quick Start

### Prerequisites
- Docker and Docker Compose installed
- Java 21 (for embedded approach)
- PostgreSQL with logical replication enabled
- Kafka cluster running

### Start Infrastructure

```bash
# Start all services (Kafka, Zookeeper, PostgreSQL)
docker-compose up -d

# Verify services are running
docker ps

# Check Kafka UI at http://localhost:8090
```

---

## Option 1: Using Your Embedded Debezium Engine

Your application already includes an embedded Debezium engine with Spring Boot integration.

### 1. Configure Database Connection

Edit `src/main/resources/cdc-config.yml`:

```yaml
database:
  type: POSTGRESQL
  host: localhost
  port: 5432
  database: cdcdb
  username: cdcuser
  password: cdcpassword

tables:
  - name: public.orders
    includeMode: INCLUDE_ALL
  - name: public.customers
    includeMode: EXCLUDE_SPECIFIED
    columnFilter:
      - password_hash
      - ssn
```

### 2. Configure Kafka

Edit `application.yml` or set environment variables:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
      compression-type: snappy

cdc:
  kafka:
    topic-pattern: "cdc.{database}.{table}"
```

### 3. Start the Application

```bash
# Build the application
mvn clean package

# Run with development profile
java -jar target/debezium-cdc-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# Or use Docker
docker build -t cdc-app .
docker run -p 8080:8080 --network cdc-network cdc-app
```

### 4. Verify CDC is Running

```bash
# Check health endpoint
curl http://localhost:8080/api/v1/cdc/health

# View metrics
curl http://localhost:8080/api/v1/cdc/metrics

# Monitor logs
tail -f logs/cdc-application.log
```

---

## Option 2: Setting Up Kafka Connect

For a distributed architecture, use Kafka Connect with standalone Debezium connectors.

### 1. Add Kafka Connect to Docker Compose

Create `docker-compose-connect.yml`:

```yaml
version: '3.8'

services:
  kafka-connect:
    image: confluentinc/cp-kafka-connect:7.5.0
    container_name: kafka-connect
    depends_on:
      kafka:
        condition: service_healthy
    ports:
      - "8083:8083"
    environment:
      CONNECT_BOOTSTRAP_SERVERS: "kafka:29092"
      CONNECT_REST_ADVERTISED_HOST_NAME: "kafka-connect"
      CONNECT_GROUP_ID: "cdc-connect-group"
      CONNECT_CONFIG_STORAGE_TOPIC: "connect-configs"
      CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR: 1
      CONNECT_OFFSET_FLUSH_INTERVAL_MS: 10000
      CONNECT_OFFSET_STORAGE_TOPIC: "connect-offsets"
      CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR: 1
      CONNECT_STATUS_STORAGE_TOPIC: "connect-status"
      CONNECT_STATUS_STORAGE_REPLICATION_FACTOR: 1
      CONNECT_KEY_CONVERTER: "org.apache.kafka.connect.json.JsonConverter"
      CONNECT_VALUE_CONVERTER: "org.apache.kafka.connect.json.JsonConverter"
      CONNECT_PLUGIN_PATH: "/usr/share/java,/usr/share/confluent-hub-components"
      CONNECT_LOG4J_LOGGERS: "io.debezium=DEBUG,org.apache.kafka.connect=DEBUG"
    command:
      - bash
      - -c
      - |
        echo "Installing Debezium connectors..."
        confluent-hub install --no-prompt debezium/debezium-connector-postgresql:2.5.4
        confluent-hub install --no-prompt debezium/debezium-connector-mysql:2.5.4
        /etc/confluent/docker/run
    networks:
      - cdc-network

networks:
  cdc-network:
    external: true
```

### 2. Start Kafka Connect

```bash
# Start Kafka Connect
docker-compose -f docker-compose-connect.yml up -d

# Wait for Kafka Connect to be ready
sleep 30

# Verify Kafka Connect is running
curl http://localhost:8083/connector-plugins | jq
```

### 3. Register PostgreSQL Debezium Connector

Create `connectors/postgres-connector.json`:

```json
{
  "name": "postgres-cdc-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "tasks.max": "1",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "cdcuser",
    "database.password": "cdcpassword",
    "database.dbname": "cdcdb",
    "database.server.name": "postgres",
    "table.include.list": "public.orders,public.customers",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_slot",
    "publication.name": "debezium_publication",
    "database.history.kafka.bootstrap.servers": "kafka:29092",
    "database.history.kafka.topic": "schema-changes.postgres",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false",
    "snapshot.mode": "initial",
    "heartbeat.interval.ms": "10000",
    "tombstones.on.delete": "false",
    "column.exclude.list": "public.customers.password_hash,public.customers.ssn",
    "transforms": "route,unwrap",
    "transforms.route.type": "org.apache.kafka.connect.transforms.RegexRouter",
    "transforms.route.regex": "([^.]+)\\.([^.]+)\\.([^.]+)",
    "transforms.route.replacement": "cdc.$1.$3",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false",
    "transforms.unwrap.delete.handling.mode": "rewrite"
  }
}
```

### 4. Deploy the Connector

```bash
# Deploy the connector
curl -X POST -H "Content-Type: application/json" \
  --data @connectors/postgres-connector.json \
  http://localhost:8083/connectors

# Check connector status
curl http://localhost:8083/connectors/postgres-cdc-connector/status | jq

# List all connectors
curl http://localhost:8083/connectors | jq
```

### 5. MySQL Connector Example

Create `connectors/mysql-connector.json`:

```json
{
  "name": "mysql-cdc-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "tasks.max": "1",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "cdcuser",
    "database.password": "cdcpassword",
    "database.server.id": "184054",
    "database.server.name": "mysql",
    "database.include.list": "inventory",
    "table.include.list": "inventory.products,inventory.orders",
    "database.history.kafka.bootstrap.servers": "kafka:29092",
    "database.history.kafka.topic": "schema-changes.mysql",
    "include.schema.changes": "true",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false",
    "snapshot.mode": "when_needed",
    "snapshot.locking.mode": "minimal"
  }
}
```

---

## PostgreSQL Configuration

### Enable Logical Replication

Edit `postgresql.conf`:

```conf
# Logical replication settings
wal_level = logical
max_replication_slots = 10
max_wal_senders = 10
```

### Create Replication User and Publication

```sql
-- Create CDC user
CREATE USER cdcuser WITH REPLICATION LOGIN PASSWORD 'cdcpassword';

-- Grant necessary permissions
GRANT CONNECT ON DATABASE cdcdb TO cdcuser;
GRANT USAGE ON SCHEMA public TO cdcuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO cdcuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO cdcuser;

-- Create publication for all tables
CREATE PUBLICATION debezium_publication FOR ALL TABLES;

-- Or create publication for specific tables
CREATE PUBLICATION debezium_publication FOR TABLE public.orders, public.customers;
```

### Verify Replication Setup

```sql
-- Check WAL level
SHOW wal_level;

-- List replication slots
SELECT * FROM pg_replication_slots;

-- List publications
SELECT * FROM pg_publication;

-- Check publication tables
SELECT * FROM pg_publication_tables;
```

---

## Monitoring and Troubleshooting

### 1. Kafka UI

Access Kafka UI at `http://localhost:8090` to:
- View topics and messages
- Monitor consumer groups
- Check connector status
- Browse schema registry

### 2. Kafka Connect REST API

```bash
# Get all connectors
curl http://localhost:8083/connectors

# Get connector config
curl http://localhost:8083/connectors/postgres-cdc-connector/config

# Get connector tasks
curl http://localhost:8083/connectors/postgres-cdc-connector/tasks

# Pause connector
curl -X PUT http://localhost:8083/connectors/postgres-cdc-connector/pause

# Resume connector
curl -X PUT http://localhost:8083/connectors/postgres-cdc-connector/resume

# Delete connector
curl -X DELETE http://localhost:8083/connectors/postgres-cdc-connector
```

### 3. Check Kafka Topics

```bash
# List all topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092

# Consume CDC messages
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic cdc.cdcdb.orders \
  --from-beginning

# Check topic configuration
docker exec -it kafka kafka-topics \
  --describe \
  --topic cdc.cdcdb.orders \
  --bootstrap-server localhost:9092
```

### 4. Application Logs

```bash
# Embedded Debezium logs
tail -f logs/cdc-application.log

# Kafka Connect logs
docker logs -f kafka-connect

# PostgreSQL replication logs
docker exec -it postgres tail -f /var/lib/postgresql/data/log/postgresql.log
```

### 5. Common Issues and Solutions

#### Issue: Connector fails to start
```bash
# Check connector status
curl http://localhost:8083/connectors/postgres-cdc-connector/status

# Check for configuration errors
docker logs kafka-connect | grep ERROR
```

#### Issue: No CDC events in Kafka
```bash
# Check replication slot
docker exec -it postgres psql -U cdcuser -d cdcdb -c "SELECT * FROM pg_replication_slots;"

# Check connector offset
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic connect-offsets \
  --from-beginning \
  --property print.key=true
```

#### Issue: High replication lag
```sql
-- Check replication lag
SELECT
  slot_name,
  pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) as lag
FROM pg_replication_slots;
```

---

## Advanced Configuration

### 1. SSL/TLS Configuration

For production environments, enable SSL:

```json
{
  "database.sslmode": "require",
  "database.sslcert": "/path/to/client-cert.pem",
  "database.sslkey": "/path/to/client-key.pem",
  "database.sslrootcert": "/path/to/ca-cert.pem"
}
```

### 2. Custom Transformations

Add custom SMTs (Single Message Transforms):

```json
{
  "transforms": "AddPrefix,ExtractField",
  "transforms.AddPrefix.type": "org.apache.kafka.connect.transforms.RegexRouter",
  "transforms.AddPrefix.regex": "(.*)",
  "transforms.AddPrefix.replacement": "production.$1",
  "transforms.ExtractField.type": "org.apache.kafka.connect.transforms.ExtractField$Value",
  "transforms.ExtractField.field": "after"
}
```

### 3. Performance Tuning

```json
{
  "max.batch.size": "10000",
  "max.queue.size": "20000",
  "poll.interval.ms": "500",
  "snapshot.fetch.size": "10240",
  "incremental.snapshot.chunk.size": "2048"
}
```

---

## Testing the Setup

### 1. Insert Test Data

```sql
-- Connect to PostgreSQL
docker exec -it postgres psql -U cdcuser -d cdcdb

-- Create test table
CREATE TABLE IF NOT EXISTS public.orders (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER,
    order_date TIMESTAMP DEFAULT NOW(),
    total_amount DECIMAL(10,2),
    status VARCHAR(20)
);

-- Insert test data
INSERT INTO public.orders (customer_id, total_amount, status)
VALUES
    (1, 99.99, 'pending'),
    (2, 149.99, 'shipped'),
    (3, 299.99, 'delivered');

-- Update a record
UPDATE public.orders SET status = 'cancelled' WHERE id = 1;

-- Delete a record
DELETE FROM public.orders WHERE id = 2;
```

### 2. Verify CDC Events

```bash
# Check Kafka topics
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic cdc.cdcdb.orders \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true
```

### 3. Monitor via Application API

```bash
# For embedded Debezium
curl http://localhost:8080/api/v1/cdc/metrics

# Check captured events
curl http://localhost:8080/api/v1/cdc/metrics/tables
```

---

## Production Checklist

- [ ] Enable SSL/TLS for database connections
- [ ] Configure authentication for Kafka Connect
- [ ] Set up monitoring and alerting
- [ ] Configure appropriate replication slot size limits
- [ ] Implement error handling and retry logic
- [ ] Set up backup and recovery procedures
- [ ] Configure log rotation
- [ ] Implement schema registry for production
- [ ] Set up distributed mode for Kafka Connect
- [ ] Configure appropriate resource limits

---

## Useful Commands Reference

```bash
# Docker commands
docker-compose up -d                    # Start services
docker-compose down                     # Stop services
docker-compose logs -f kafka           # Follow Kafka logs
docker ps                               # List running containers

# Kafka commands
kafka-topics --list                     # List topics
kafka-console-consumer --topic <topic>  # Consume messages
kafka-consumer-groups --list           # List consumer groups

# PostgreSQL commands
\dt                                     # List tables
\l                                      # List databases
SELECT * FROM pg_stat_replication;     # Check replication status

# Application commands (Embedded Debezium)
mvn clean package                       # Build application
java -jar target/*.jar                  # Run application
curl http://localhost:8080/api/v1/cdc/health  # Health check
```

---

## Next Steps

1. **Configure Tables**: Edit `cdc-config.yml` to specify which tables to capture
2. **Set Up Monitoring**: Configure Prometheus/Grafana for metrics
3. **Test Failover**: Simulate failures and verify recovery
4. **Optimize Performance**: Tune batch sizes and polling intervals
5. **Implement Schema Evolution**: Handle DDL changes properly

For more information, refer to:
- [Debezium Documentation](https://debezium.io/documentation/)
- [Kafka Connect Documentation](https://docs.confluent.io/platform/current/connect/)
- Your application's Swagger UI: http://localhost:8080/swagger-ui.html