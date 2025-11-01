# Quickstart Guide: Debezium CDC Application

**Feature**: Configurable Debezium CDC Application
**Purpose**: Get the CDC application running locally for development and testing
**Time**: ~15 minutes

## Prerequisites

- **Java 21** (JDK): [Download from Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21)
- **Docker** and **Docker Compose**: For running PostgreSQL and Kafka locally
- **Maven**: Included via Maven Wrapper (mvnw) in the project
- **Git**: For cloning the repository

## Quick Start (5 minutes)

### Step 1: Clone and Navigate to Project

```bash
git clone <repository-url>
cd cdc-chubb-poc
```

### Step 2: Start Infrastructure with Docker Compose

```bash
# Start PostgreSQL, Kafka, and Zookeeper
docker-compose up -d

# Verify services are running
docker-compose ps
```

Expected output:
```
NAME                    STATUS          PORTS
cdc-postgres            Up              0.0.0.0:5432->5432/tcp
cdc-kafka               Up              0.0.0.0:9092->9092/tcp
cdc-zookeeper           Up              2181/tcp
```

### Step 3: Build the Application

```bash
# Build using Maven Wrapper (no need to install Maven)
./mvnw clean package

# Skip tests for quick build (not recommended for production)
./mvnw clean package -DskipTests
```

### Step 4: Run the Application

```bash
# Run with dev profile (uses local PostgreSQL and Kafka from docker-compose)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The application will start on http://localhost:8080

### Step 5: Verify CDC is Working

1. **Check Application Health**:
```bash
curl http://localhost:8080/api/v1/cdc/health | jq
```

2. **Check Monitored Tables**:
```bash
curl http://localhost:8080/api/v1/cdc/config/tables | jq
```

3. **Make a Database Change**:
```bash
# Connect to PostgreSQL
docker exec -it cdc-postgres psql -U cdcuser -d cdcdb

# Insert a test row
INSERT INTO public.orders (customer_id, status, total_amount)
VALUES (123, 'PENDING', 99.99);

# Exit psql
\q
```

4. **Verify Event in Kafka**:
```bash
# Connect to Kafka container
docker exec -it cdc-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic cdc.cdcdb.orders \
  --from-beginning \
  --max-messages 1
```

You should see a JSON change event!

## Detailed Setup

### Database Setup

The docker-compose file creates a PostgreSQL database with:
- Database: `cdcdb`
- User: `cdcuser`
- Password: `cdcpassword`
- Port: `5432`

**Create Sample Tables**:

```bash
docker exec -it cdc-postgres psql -U cdcuser -d cdcdb
```

```sql
-- Orders table
CREATE TABLE IF NOT EXISTS public.orders (
    order_id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Customers table
CREATE TABLE IF NOT EXISTS public.customers (
    customer_id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Grant replication privileges for CDC
ALTER TABLE public.orders REPLICA IDENTITY FULL;
ALTER TABLE public.customers REPLICA IDENTITY FULL;

\q
```

### Configuration

**Location**: `src/main/resources/application-dev.yml`

```yaml
# CDC Source Database Configuration
cdc:
  database:
    type: POSTGRESQL
    host: localhost
    port: 5432
    database: cdcdb
    username: cdcuser
    password: cdcpassword

  # Tables to monitor
  tables:
    - name: public.orders
      includeMode: INCLUDE_ALL
    - name: public.customers
      includeMode: EXCLUDE_SPECIFIED
      columnFilter: [password_hash]  # Exclude sensitive columns

  # Kafka Configuration
  kafka:
    brokers:
      - localhost:9092
    topicPattern: "cdc.{database}.{table}"
```

**Environment Variables** (optional):

Create `.env` file:
```bash
DB_PASSWORD=cdcpassword
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
CDC_CONFIG_PATH=config/cdc-config.yml
```

### Running Tests

```bash
# Run all tests (unit + integration + contract tests)
./mvnw test

# Run only unit tests
./mvnw test -Dgroups="unit"

# Run integration tests (uses Testcontainers - requires Docker)
./mvnw test -Dgroups="integration"

# Run with coverage report
./mvnw test jacoco:report
# View report: target/site/jacoco/index.html
```

### Using the CLI

The application provides a CLI for management operations:

```bash
# Validate configuration file
java -jar target/cdc-app.jar config validate --file=config/cdc-config.yml

# Show current status (JSON output)
java -jar target/cdc-app.jar status --json

# Start CDC engine (if not auto-started)
java -jar target/cdc-app.jar start

# Stop CDC engine gracefully
java -jar target/cdc-app.jar stop
```

## Monitoring and Debugging

### View Application Logs

```bash
# Tail logs
tail -f logs/application.log

# Search for errors
grep ERROR logs/application.log

# View structured logs (JSON format in production)
cat logs/application.log | jq 'select(.level == "ERROR")'
```

### Check Metrics

```bash
# Overall CDC metrics
curl http://localhost:8080/api/v1/cdc/metrics | jq

# Per-table metrics
curl http://localhost:8080/api/v1/cdc/metrics/tables | jq

# Specific table metrics
curl http://localhost:8080/api/v1/cdc/metrics/tables | jq '.[] | select(.table.table == "orders")'
```

### Access OpenAPI Documentation

Once the application is running, visit:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### Kafka Topics

List all CDC topics:
```bash
docker exec -it cdc-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list | grep cdc
```

Consume from specific topic:
```bash
docker exec -it cdc-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic cdc.cdcdb.orders \
  --from-beginning
```

### Database CDC Position

Check current offset:
```bash
curl http://localhost:8080/api/v1/cdc/offset/position | jq
```

View offset file (file-based storage):
```bash
cat data/offsets/cdc-offsets.dat
```

## Troubleshooting

### Issue: Application won't start - "Connection refused" to PostgreSQL

**Solution**:
```bash
# Check PostgreSQL is running
docker ps | grep cdc-postgres

# Check PostgreSQL logs
docker logs cdc-postgres

# Restart PostgreSQL
docker-compose restart cdc-postgres
```

### Issue: No events appearing in Kafka

**Diagnosis**:
```bash
# 1. Check CDC engine health
curl http://localhost:8080/api/v1/cdc/health | jq '.engineHealth'

# 2. Check monitored tables
curl http://localhost:8080/api/v1/cdc/config/tables | jq

# 3. Verify table has REPLICA IDENTITY
docker exec -it cdc-postgres psql -U cdcuser -d cdcdb \
  -c "SELECT relreplident FROM pg_class WHERE relname = 'orders';"
# Should return 'f' (full) or 'd' (default with PK)
```

**Solution**:
```sql
-- Set REPLICA IDENTITY FULL for table
ALTER TABLE public.orders REPLICA IDENTITY FULL;
```

### Issue: Kafka connection errors

**Diagnosis**:
```bash
# Check Kafka is running and accessible
docker exec -it cdc-kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092

# Check Kafka health from application
curl http://localhost:8080/api/v1/cdc/health/kafka | jq
```

**Solution**:
```bash
# Restart Kafka
docker-compose restart cdc-kafka cdc-zookeeper

# Check Kafka logs
docker logs cdc-kafka
```

### Issue: High memory usage

**Diagnosis**:
```bash
# Check JVM memory usage
jps -lv | grep cdc

# Get heap dump
jmap -heap <pid>
```

**Solution**:
Adjust JVM options in `application-dev.yml`:
```yaml
spring:
  application:
    name: debezium-cdc-app

# JVM options for development
java:
  opts: "-Xmx512m -Xms256m"
```

## Next Steps

1. **Add More Tables**: Edit `application-dev.yml` to monitor additional tables
2. **Test Schema Changes**: ALTER TABLE and verify CDC continues capturing
3. **Explore REST API**: Use Swagger UI to explore all management endpoints
4. **Review Change Events**: Examine the JSON structure of captured events in Kafka
5. **Run Integration Tests**: Execute full test suite with `./mvnw verify`

## Development Workflow

### Making Configuration Changes

1. Edit `src/main/resources/application-dev.yml`
2. Restart the application (Spring Boot will reload)
3. Verify changes: `curl http://localhost:8080/api/v1/cdc/config/status | jq`

**OR** for hot reload (if enabled):

1. Edit configuration file
2. Wait 5 minutes for automatic refresh
3. Check refresh status: `curl http://localhost:8080/api/v1/cdc/metrics | jq`

### Testing Database Changes

```sql
-- Connect to database
docker exec -it cdc-postgres psql -U cdcuser -d cdcdb

-- Test INSERT
INSERT INTO public.orders (customer_id, status, total_amount)
VALUES (456, 'CONFIRMED', 149.99);

-- Test UPDATE
UPDATE public.orders SET status = 'SHIPPED' WHERE order_id = 1;

-- Test DELETE
DELETE FROM public.orders WHERE order_id = 2;
```

Each operation should produce a corresponding event in Kafka topic `cdc.cdcdb.orders`.

### Building for Production

```bash
# Build Docker image
docker build -t cdc-app:latest .

# Run containerized application
docker run -d \
  -p 8080:8080 \
  -e DB_PASSWORD=prodpassword \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka-prod:9092 \
  -v /path/to/config:/config \
  cdc-app:latest
```

## Resources

- **Architecture**: See `specs/001-debezium-cdc-app/plan.md`
- **Data Model**: See `specs/001-debezium-cdc-app/data-model.md`
- **API Contracts**: See `specs/001-debezium-cdc-app/contracts/`
- **Debezium Docs**: https://debezium.io/documentation/
- **Spring Boot Docs**: https://docs.spring.io/spring-boot/docs/current/reference/html/
- **Kafka Docs**: https://kafka.apache.org/documentation/

## Support

For issues or questions:
- Check logs: `logs/application.log`
- View health status: http://localhost:8080/api/v1/cdc/health
- Review spec: `specs/001-debezium-cdc-app/spec.md`
