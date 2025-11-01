# Debezium CDC Application

A configurable Change Data Capture (CDC) application using Debezium that captures row-level database changes and publishes them to Kafka topics.

## Features

- **Build Once, Deploy Many**: Single application binary configurable for multiple source systems
- **Multi-Database Support**: PostgreSQL and MySQL (with extensibility for SQL Server, Oracle)
- **Change Data Capture**: Real-time capture of INSERT, UPDATE, DELETE operations
- **Data Normalization**: Consistent data format across different database types
- **Hot Configuration Reload**: Update monitored tables without restarting the application
- **Schema Evolution**: Handle database schema changes without stopping CDC
- **Health Monitoring**: REST API for health checks, metrics, and operational status
- **Clean Architecture**: Domain-driven design with bounded contexts

## Quick Start

See [specs/001-debezium-cdc-app/quickstart.md](specs/001-debezium-cdc-app/quickstart.md) for detailed setup instructions.

### Prerequisites

- Java 21 (JDK)
- Docker and Docker Compose
- Maven (or use included Maven Wrapper)

### Start Infrastructure

```bash
# Start PostgreSQL, Kafka, and Zookeeper
docker-compose up -d

# Verify services are running
docker-compose ps
```

### Build and Run

```bash
# Build the application
./mvnw clean package

# Run with development profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Verify CDC is Working

```bash
# Check health
curl http://localhost:8080/api/v1/cdc/health | jq

# Check monitored tables
curl http://localhost:8080/api/v1/cdc/config/tables | jq
```

## Architecture

The application follows Clean Architecture with Domain-Driven Design:

- **Domain Layer**: Framework-independent business logic
  - Change Capture Context: CDC events, position tracking
  - Configuration Context: Database/table configuration management
  - Health Monitoring Context: System health and metrics

- **Application Layer**: Use cases and orchestration
- **Infrastructure Layer**: Adapters for Debezium, Kafka, file storage
- **Presentation Layer**: REST API and CLI

## Configuration

Configuration is managed via YAML files. See `src/main/resources/application-dev.yml` for an example.

```yaml
cdc:
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
      includeMode: INCLUDE_ALL

  kafka:
    bootstrap-servers: localhost:9092
    topic-pattern: "cdc.{database}.{table}"
```

## API Documentation

Once running, access Swagger UI at: http://localhost:8080/swagger-ui.html

## Testing

```bash
# Run all tests
./mvnw test

# Run integration tests (requires Docker)
./mvnw test -Dgroups="integration"

# Generate coverage report
./mvnw test jacoco:report
```

## Deployment

### Docker

```bash
# Build Docker image
docker build -t cdc-app:latest .

# Run container
docker run -d \
  -p 8080:8080 \
  -v /path/to/config:/config \
  -v /path/to/data:/data \
  -e CDC_CONFIG_PATH=/config/cdc-config.yml \
  cdc-app:latest
```

### Kubernetes

Kubernetes manifests are available in the `k8s/` directory. Use Kustomize for environment-specific deployments.

```bash
# Deploy to development
kubectl apply -k k8s/dev/

# Deploy to production
kubectl apply -k k8s/prod/
```

## Development

### Project Structure

```
src/
├── main/
│   ├── java/com/chubb/cdc/debezium/
│   │   ├── domain/          # Domain layer (no framework dependencies)
│   │   ├── application/     # Use cases and DTOs
│   │   ├── infrastructure/  # Adapters and implementations
│   │   └── presentation/    # REST controllers and CLI
│   └── resources/
│       ├── application.yml
│       └── logback-spring.xml
└── test/
    └── java/com/chubb/cdc/debezium/
        ├── unit/
        ├── integration/
        ├── contract/
        └── architecture/
```

### Code Quality

- **ArchUnit Tests**: Enforce architecture rules
- **Test Coverage**: Target >80% for use cases and domain logic
- **Code Style**: Follow Java conventions, use Lombok for boilerplate

## Monitoring

### Metrics

```bash
# Overall CDC metrics
curl http://localhost:8080/api/v1/cdc/metrics | jq

# Per-table metrics
curl http://localhost:8080/api/v1/cdc/metrics/tables | jq
```

### Logs

Structured JSON logs in production, human-readable logs in development.

```bash
# View logs
tail -f logs/application.log

# Search for errors (production JSON logs)
cat logs/application.log | jq 'select(.level == "ERROR")'
```

## Documentation

- **Feature Specification**: [specs/001-debezium-cdc-app/spec.md](specs/001-debezium-cdc-app/spec.md)
- **Implementation Plan**: [specs/001-debezium-cdc-app/plan.md](specs/001-debezium-cdc-app/plan.md)
- **Data Model**: [specs/001-debezium-cdc-app/data-model.md](specs/001-debezium-cdc-app/data-model.md)
- **API Contracts**: [specs/001-debezium-cdc-app/contracts/](specs/001-debezium-cdc-app/contracts/)

## License

Proprietary - Chubb Corporation

## Support

For issues or questions, please contact the development team.
