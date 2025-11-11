# Debezium Custom Format Guide

This guide explains how to configure Kafka Connect Debezium PostgreSQL connector to output CDC data in your desired custom format.

## Desired Output Format

```json
{
    "table": {
        "database": "cdcdb",
        "schema": "public",
        "table": "customers"
    },
    "operation": "UPDATE",
    "timestamp": "2025-11-03T06:25:07.179Z",
    "position": {
        "sourcePartition": "{server=postgres-localhost-cdcdb}",
        "offset": {
            "lsn_commit": 28744688,
            "lsn_proc": 28744744,
            "txId": 810,
            "messageType": "UPDATE",
            "ts_usec": 1762151107093992,
            "lsn": 28744744
        }
    },
    "before": { /* row data before change */ },
    "after": { /* row data after change */ },
    "metadata": {
        "version": "1.0.0",
        "schemaVersion": 1,
        "connector": "postgresql",
        "source": "debezium-cdc-app"
    }
}
```

## Three Approaches to Achieve This Format

### 1. Basic Approach: Full Debezium Envelope (No Transformation)

**File:** `connectors/postgres-connector-full-envelope.json`

This configuration removes the `ExtractNewRecordState` transform and keeps the full Debezium envelope. The output will be similar but not exactly in your desired format:

```json
{
    "before": { /* row data */ },
    "after": { /* row data */ },
    "source": {
        "version": "2.3.0.Final",
        "connector": "postgresql",
        "name": "postgres-full",
        "ts_ms": 1762151107179,
        "db": "cdcdb",
        "schema": "public",
        "table": "customers",
        "lsn": 28744744,
        "xmin": null
    },
    "op": "u",
    "ts_ms": 1762151107179,
    "transaction": null
}
```

**To use this approach:**
1. Deploy the connector configuration
2. Transform the data in your application code

### 2. Custom SMT Approach (Recommended)

**Files:**
- `connectors/postgres-connector-custom-transform.json` - Connector config
- `src/main/java/com/example/kafka/transforms/DebeziumCustomTransform.java` - Custom SMT

This approach uses a custom Single Message Transform (SMT) to transform the Debezium format into your exact desired format at the connector level.

**Steps to implement:**

1. Build and package the custom SMT:
```bash
# Add to your pom.xml dependencies
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>connect-api</artifactId>
    <version>3.5.0</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>connect-transforms</artifactId>
    <version>3.5.0</version>
</dependency>

# Build the JAR
mvn clean package

# Copy the JAR to Kafka Connect plugins directory
cp target/your-transforms.jar /kafka/connect/plugins/
```

2. Deploy the connector with custom transform:
```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @connectors/postgres-connector-custom-transform.json
```

### 3. Kafka Streams Approach

**File:** `src/main/java/com/example/kafka/streams/DebeziumTransformProcessor.java`

This approach uses a separate Kafka Streams application to read from Debezium topics and transform the data.

**Steps to implement:**

1. Add Kafka Streams dependencies to `pom.xml`:
```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-streams</artifactId>
    <version>3.5.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
</dependency>
```

2. Run the Kafka Streams application:
```bash
java -cp target/your-app.jar com.example.kafka.streams.DebeziumTransformProcessor
```

## Comparison of Approaches

| Approach | Pros | Cons |
|----------|------|------|
| **Full Envelope** | Simple, no custom code needed | Requires application-side transformation |
| **Custom SMT** | Transformation at source, consistent format | Requires custom Java code, deployment complexity |
| **Kafka Streams** | Flexible, can add complex logic | Additional infrastructure, higher latency |

## Key Configuration Differences

### Original Configuration Issues:
1. **`ExtractNewRecordState` transform**: Removes envelope, loses "before" state
2. **Column exclusion**: Removes sensitive data but loses it completely
3. **Topic routing**: Changes topic names but doesn't change message format

### Fixed Configuration Features:
1. **No unwrap transform**: Keeps full change event envelope
2. **Custom SMT**: Transforms to exact desired format
3. **Proper timestamp formatting**: ISO 8601 format
4. **Metadata enrichment**: Adds custom metadata fields

## Testing the Configuration

1. **Create test data:**
```sql
-- Connect to PostgreSQL
psql -h localhost -U cdcuser -d cdcdb

-- Update a customer record
UPDATE customers
SET last_name = 'TestUpdate'
WHERE customer_id = 4;
```

2. **Consume messages:**
```bash
# For full envelope approach
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic cdc-full.customers \
  --from-beginning \
  --property print.key=true

# For custom SMT approach
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic postgres-custom.public.customers \
  --from-beginning \
  --property print.key=true

# For Kafka Streams approach
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic cdc-transformed-customers \
  --from-beginning \
  --property print.key=true
```

## Production Considerations

1. **Performance**: Custom SMT adds processing overhead at the connector level
2. **Error Handling**: Implement proper error handling in custom transforms
3. **Schema Registry**: Consider using Avro with Schema Registry for production
4. **Monitoring**: Add metrics to track transformation success/failure rates
5. **Versioning**: Implement versioning strategy for transform logic changes

## Troubleshooting

### Common Issues:

1. **Transform class not found**:
   - Ensure JAR is in Connect plugins directory
   - Restart Kafka Connect workers

2. **Malformed JSON output**:
   - Check timestamp formatting
   - Verify null handling in transform

3. **Missing before/after data**:
   - Ensure WAL level is set to `logical` in PostgreSQL
   - Check connector snapshot mode configuration

### Debug Logging:
```properties
# Add to connect-log4j.properties
log4j.logger.com.example.kafka.transforms=DEBUG
log4j.logger.io.debezium=DEBUG
```

## Next Steps

1. Choose the approach that best fits your architecture
2. Implement error handling and monitoring
3. Test with production-like data volumes
4. Consider implementing data masking for sensitive fields
5. Set up proper backup and recovery procedures