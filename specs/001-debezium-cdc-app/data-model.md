# Data Model: Debezium CDC Application

**Feature**: Configurable Debezium CDC Application
**Date**: 2025-11-01
**Purpose**: Define domain entities, value objects, aggregates, and their relationships per DDD tactical patterns

## Bounded Contexts

### 1. Change Capture Context

Core CDC domain logic - capturing change events, tracking position, handling schema evolution.

#### Value Objects (Records - Immutable)

**ChangeEvent**
```java
public record ChangeEvent(
    TableIdentifier table,
    OperationType operation,
    Instant timestamp,
    CdcPosition position,
    RowData before,  // null for INSERT
    RowData after,   // null for DELETE
    Map<String, Object> metadata
) {
    // Validation: operation must match before/after presence
    // INSERT: before must be null, after must be present
    // UPDATE: both before and after must be present
    // DELETE: before must be present, after must be null
}
```

**OperationType** (Enum)
```java
public enum OperationType {
    INSERT,
    UPDATE,
    DELETE
}
```

**TableIdentifier**
```java
public record TableIdentifier(
    String database,
    String schema,  // may be null for databases without schema concept
    String table
) {
    public String fullyQualifiedName() {
        return schema != null
            ? String.format("%s.%s.%s", database, schema, table)
            : String.format("%s.%s", database, table);
    }
}
```

**CdcPosition**
```java
public record CdcPosition(
    String sourcePartition,  // identifies which database/connector
    Map<String, Object> offset  // opaque offset data (LSN, binlog position, etc.)
) implements Comparable<CdcPosition> {
    // Comparison based on offset timestamp or sequence number
    // Used to determine if position A is before or after position B
}
```

**RowData**
```java
public record RowData(
    Map<String, Object> fields  // column name -> normalized value
) {
    // All values are normalized (ISO-8601 timestamps, JSON numbers, UTF-8 text)
    // Immutable map
}
```

#### Domain Events

**CaptureStartedEvent**
```java
public record CaptureStartedEvent(
    Instant timestamp,
    TableIdentifier table,
    CdcPosition initialPosition
) {}
```

**CaptureStoppedEvent**
```java
public record CaptureStoppedEvent(
    Instant timestamp,
    TableIdentifier table,
    CdcPosition finalPosition,
    String reason  // GRACEFUL_SHUTDOWN, ERROR, CONFIGURATION_CHANGE
) {}
```

**SchemaChangedEvent**
```java
public record SchemaChangedEvent(
    Instant timestamp,
    TableIdentifier table,
    SchemaChange change
) {}

public record SchemaChange(
    SchemaChangeType type,  // COLUMN_ADDED, COLUMN_REMOVED, COLUMN_RENAMED, TYPE_CHANGED
    String columnName,
    String oldType,  // may be null
    String newType   // may be null
) {}
```

#### Repository Ports

**OffsetRepository**
```java
public interface OffsetRepository {
    void save(CdcPosition position);
    Optional<CdcPosition> load(String sourcePartition);
    void delete(String sourcePartition);
}
```

---

### 2. Configuration Context

Manages database connections, table monitoring rules, Kafka destination configuration.

#### Aggregate Root

**ConfigurationAggregate**
```java
public class ConfigurationAggregate {
    private final SourceDatabaseConfig databaseConfig;
    private final Set<TableConfig> tableConfigs;
    private final KafkaConfig kafkaConfig;
    private final Instant loadedAt;

    // Validation rules:
    // - At least one table must be configured
    // - Database connection details must be valid
    // - Kafka broker list must not be empty
    // - Topic naming pattern must be valid

    public void validate() throws InvalidConfigurationException;
    public boolean hasChangedSince(ConfigurationAggregate other);
    public Set<TableIdentifier> addedTables(ConfigurationAggregate previousConfig);
    public Set<TableIdentifier> removedTables(ConfigurationAggregate previousConfig);
}
```

#### Entity

**SourceDatabaseConfig**
```java
public class SourceDatabaseConfig {
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;  // encrypted in storage
    private final SslConfig sslConfig;  // may be null
    private final Map<String, String> additionalProperties;

    // Validation: host, port, database, username, password required
    // Port must be valid range (1-65535)
    // SSL config validated if present
}
```

**DatabaseType** (Enum)
```java
public enum DatabaseType {
    POSTGRESQL,
    MYSQL,
    SQLSERVER,
    ORACLE
}
```

#### Value Objects

**TableConfig**
```java
public record TableConfig(
    TableIdentifier table,
    IncludeMode includeMode,  // INCLUDE_ALL, EXCLUDE_SPECIFIED
    Set<String> columnFilter,  // empty = all columns
    Optional<CompositeUniqueKey> compositeKey  // for tables without PK
) {
    // Validation: if compositeKey present, columns must exist in table
}
```

**CompositeUniqueKey**
```java
public record CompositeUniqueKey(
    List<String> columnNames
) {
    // Validation: at least one column, no duplicates
}
```

**KafkaConfig**
```java
public record KafkaConfig(
    List<String> brokerAddresses,
    String topicNamePattern,  // e.g., "cdc.{database}.{table}"
    KafkaSecurity security,  // may be null
    Map<String, Object> producerProperties
) {
    // Validation: broker list not empty, topic pattern contains placeholders
}
```

**KafkaSecurity**
```java
public record KafkaSecurity(
    SecurityProtocol protocol,  // SSL, SASL_SSL, SASL_PLAINTEXT
    SaslMechanism mechanism,  // PLAIN, SCRAM-SHA-256, SCRAM-SHA-512
    String username,
    String password,
    SslTruststore truststore
) {}
```

**SslConfig** (for database connections)
```java
public record SslConfig(
    boolean enabled,
    SslMode mode,  // REQUIRE, VERIFY_CA, VERIFY_FULL
    Optional<String> caCertPath,
    Optional<String> clientCertPath,
    Optional<String> clientKeyPath
) {}
```

#### Domain Events

**ConfigurationLoadedEvent**
```java
public record ConfigurationLoadedEvent(
    Instant timestamp,
    ConfigurationAggregate configuration,
    String source  // file path or config server URL
) {}
```

**ConfigurationChangedEvent**
```java
public record ConfigurationChangedEvent(
    Instant timestamp,
    ConfigurationAggregate oldConfig,
    ConfigurationAggregate newConfig,
    Set<TableIdentifier> addedTables,
    Set<TableIdentifier> removedTables
) {}
```

#### Repository Ports

**ConfigurationRepository**
```java
public interface ConfigurationRepository {
    ConfigurationAggregate load() throws ConfigurationLoadException;
    Instant lastModified();
    void watch(Consumer<ConfigurationChangedEvent> listener);
}
```

---

### 3. Health Monitoring Context

Tracks system health (database connectivity, Kafka availability), collects capture metrics.

#### Entity

**HealthStatus**
```java
public class HealthStatus {
    private HealthState overall State;
    private final DatabaseHealthCheck databaseHealth;
    private final KafkaHealthCheck kafkaHealth;
    private final CdcEngineHealthCheck engineHealth;
    private final Instant lastChecked;

    // Derived state: overall = UP only if all components UP
    public HealthState getOverallState();
    public boolean isHealthy();
}
```

**HealthState** (Enum)
```java
public enum HealthState {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN
}
```

#### Value Objects

**DatabaseHealthCheck**
```java
public record DatabaseHealthCheck(
    HealthState state,
    String message,
    Instant checkedAt,
    Optional<Duration> connectionTime,
    Optional<String> errorMessage
) {}
```

**KafkaHealthCheck**
```java
public record KafkaHealthCheck(
    HealthState state,
    String message,
    Instant checkedAt,
    int availableBrokers,
    Optional<String> errorMessage
) {}
```

**CdcEngineHealthCheck**
```java
public record CdcEngineHealthCheck(
    HealthState state,
    String message,
    Instant checkedAt,
    boolean isCapturing,
    int monitoredTables,
    Optional<String> errorMessage
) {}
```

**CaptureMetrics**
```java
public record CaptureMetrics(
    long eventsCapture,
    long eventsPublished,
    long eventsFailed,
    Duration captureLatencyP50,
    Duration captureLatencyP95,
    Duration captureLatencyP99,
    Instant periodStart,
    Instant periodEnd
) {}
```

**TableMetrics**
```java
public record TableMetrics(
    TableIdentifier table,
    long insertCount,
    long updateCount,
    long deleteCount,
    Instant lastEventTimestamp,
    CdcPosition lastPosition
) {}
```

#### Repository Ports

**MetricsRepository**
```java
public interface MetricsRepository {
    void recordEvent(ChangeEvent event, Duration captureLatency);
    CaptureMetrics getMetricsSince(Instant start);
    List<TableMetrics> getTableMetrics();
    void reset();
}
```

---

## Domain Model Relationships

```
ConfigurationAggregate
  │
  ├─ 1:1 ─> SourceDatabaseConfig (entity)
  ├─ 1:N ─> TableConfig (value object)
  └─ 1:1 ─> KafkaConfig (value object)

ChangeEvent (value object)
  │
  ├─ 1:1 ─> TableIdentifier (value object)
  ├─ 1:1 ─> OperationType (enum)
  ├─ 1:1 ─> CdcPosition (value object)
  ├─ 0:1 ─> RowData (before - value object)
  └─ 0:1 ─> RowData (after - value object)

HealthStatus (entity)
  │
  ├─ 1:1 ─> DatabaseHealthCheck (value object)
  ├─ 1:1 ─> KafkaHealthCheck (value object)
  └─ 1:1 ─> CdcEngineHealthCheck (value object)
```

## Invariants and Business Rules

### Change Capture Context

1. **Event Integrity**: ChangeEvent must have valid operation-to-data correspondence:
   - INSERT: `before == null && after != null`
   - UPDATE: `before != null && after != null`
   - DELETE: `before != null && after == null`

2. **Position Ordering**: CdcPosition must be comparable to support "is after" queries for recovery

3. **Timestamp Consistency**: All timestamps must be in UTC, ISO-8601 format

### Configuration Context

1. **Table Uniqueness**: No duplicate TableIdentifier in tableConfigs set

2. **Composite Key Validity**: If TableConfig specifies CompositeUniqueKey, those columns must exist (validated at runtime)

3. **Kafka Topic Pattern**: Topic name pattern must contain `{database}` and `{table}` placeholders

4. **Database Connectivity**: SourceDatabaseConfig must pass connection test before being accepted

### Health Monitoring Context

1. **Overall Health Derivation**: `overallState = UP` only if all component checks are UP

2. **Metrics Accuracy**: eventsPublished ≤ eventsCaptured (can't publish more than captured)

3. **Latency Percentiles**: P50 ≤ P95 ≤ P99 (mathematical invariant)

## Data Validation Rules

### At Configuration Load Time

- Database host must be resolvable hostname or valid IP
- Port must be in range 1-65535
- Kafka broker addresses must be in `host:port` format
- SSL certificates must exist at specified paths if SSL enabled
- Table names must match database identifier rules (no SQL injection)

### At Runtime (Event Capture)

- Row data field values must be JSON-serializable after normalization
- Timestamps must be valid ISO-8601 strings
- Numeric values must fit in JSON number range
- Text must be valid UTF-8

### At Health Check Time

- Database connection test timeout: 10 seconds
- Kafka broker metadata fetch timeout: 5 seconds
- Health check must complete within 15 seconds total

## Persistence Mapping

### Domain → Infrastructure

**CdcPosition** → File Storage:
```json
{
  "sourcePartition": "server1-db1",
  "offset": {
    "lsn": "0/12345678",  // PostgreSQL
    "timestamp": 1234567890
  }
}
```

**ConfigurationAggregate** → YAML File:
```yaml
database:
  type: POSTGRESQL
  host: localhost
  port: 5432
  database: mydb
  username: cdcuser
  # password from environment variable

tables:
  - name: public.orders
    includeMode: INCLUDE_ALL
  - name: public.customers
    includeMode: INCLUDE_ALL
    compositeKey:
      columns: [email, registration_date]

kafka:
  brokers:
    - localhost:9092
  topicPattern: "cdc.{database}.{table}"
```

**ChangeEvent** → Kafka Message:
```json
{
  "table": {
    "database": "mydb",
    "schema": "public",
    "table": "orders"
  },
  "operation": "UPDATE",
  "timestamp": "2025-11-01T10:30:00Z",
  "position": {
    "sourcePartition": "server1-mydb",
    "offset": {"lsn": "0/12345678"}
  },
  "before": {
    "order_id": 123,
    "status": "PENDING",
    "updated_at": "2025-11-01T10:00:00Z"
  },
  "after": {
    "order_id": 123,
    "status": "CONFIRMED",
    "updated_at": "2025-11-01T10:30:00Z"
  },
  "metadata": {
    "source": "debezium-cdc-app",
    "version": "1.0.0"
  }
}
```

## Evolution Considerations

### Schema Changes

- Add new fields to ChangeEvent metadata without breaking consumers (additive changes only)
- Configuration schema versioning: include `schemaVersion: 1` field, support migration
- Metrics: add new metric types without removing existing ones

### Backward Compatibility

- New OperationType values must be additive (don't remove existing)
- DatabaseType enum can grow, old types must remain supported
- Kafka topic pattern: support both old and new patterns during migration period

## Summary

The data model defines **3 bounded contexts** with clear responsibilities:

1. **Change Capture**: Immutable value objects (ChangeEvent, CdcPosition, RowData) represent captured data
2. **Configuration**: Aggregate (ConfigurationAggregate) with validation rules, entities (SourceDatabaseConfig), value objects (TableConfig, KafkaConfig)
3. **Health Monitoring**: Entity (HealthStatus) with value objects for component health checks and metrics

All domain objects are **framework-independent** (no Spring, no Debezium in domain layer), supporting Clean Architecture principle VI.
