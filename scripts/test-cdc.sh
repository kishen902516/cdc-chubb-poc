#!/bin/bash

# Test CDC Setup
# This script tests the CDC pipeline by inserting, updating, and deleting test data

set -e

echo "==================================="
echo "CDC Test Script"
echo "==================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
POSTGRES_CONTAINER="postgres"
KAFKA_CONTAINER="kafka"
DB_NAME="cdcdb"
DB_USER="cdcuser"
TEST_TABLE="test_cdc_$(date +%s)"

# Function to execute PostgreSQL commands
exec_psql() {
    docker exec -i $POSTGRES_CONTAINER psql -U $DB_USER -d $DB_NAME -c "$1"
}

# Function to consume Kafka messages
consume_kafka() {
    local topic=$1
    local duration=${2:-5}

    echo "Consuming from topic: $topic for ${duration} seconds..."
    timeout $duration docker exec $KAFKA_CONTAINER \
        kafka-console-consumer \
        --bootstrap-server localhost:9092 \
        --topic $topic \
        --from-beginning \
        --property print.timestamp=true \
        --property print.key=true \
        2>/dev/null || true
}

# Create test table
create_test_table() {
    echo -e "\n${YELLOW}Creating test table: $TEST_TABLE${NC}"

    exec_psql "
    CREATE TABLE IF NOT EXISTS public.$TEST_TABLE (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100),
        email VARCHAR(100),
        age INTEGER,
        created_at TIMESTAMP DEFAULT NOW(),
        updated_at TIMESTAMP DEFAULT NOW()
    );"

    echo -e "${GREEN}✓ Table created${NC}"
}

# Insert test data
insert_test_data() {
    echo -e "\n${YELLOW}Inserting test data...${NC}"

    exec_psql "
    INSERT INTO public.$TEST_TABLE (name, email, age) VALUES
        ('John Doe', 'john@example.com', 30),
        ('Jane Smith', 'jane@example.com', 25),
        ('Bob Johnson', 'bob@example.com', 35),
        ('Alice Brown', 'alice@example.com', 28),
        ('Charlie Wilson', 'charlie@example.com', 45);"

    echo -e "${GREEN}✓ 5 records inserted${NC}"
}

# Update test data
update_test_data() {
    echo -e "\n${YELLOW}Updating test data...${NC}"

    exec_psql "
    UPDATE public.$TEST_TABLE
    SET age = age + 1,
        updated_at = NOW()
    WHERE age < 35;"

    exec_psql "
    UPDATE public.$TEST_TABLE
    SET email = REPLACE(email, '@example.com', '@test.com'),
        updated_at = NOW()
    WHERE name LIKE 'J%';"

    echo -e "${GREEN}✓ Records updated${NC}"
}

# Delete test data
delete_test_data() {
    echo -e "\n${YELLOW}Deleting test data...${NC}"

    exec_psql "DELETE FROM public.$TEST_TABLE WHERE age > 40;"

    echo -e "${GREEN}✓ Records deleted${NC}"
}

# Complex operations
complex_operations() {
    echo -e "\n${YELLOW}Performing complex operations...${NC}"

    # Transaction with multiple operations
    exec_psql "
    BEGIN;
    INSERT INTO public.$TEST_TABLE (name, email, age)
        VALUES ('Transaction User', 'trans@example.com', 22);
    UPDATE public.$TEST_TABLE
        SET age = 23
        WHERE name = 'Transaction User';
    COMMIT;"

    # Upsert operation
    exec_psql "
    INSERT INTO public.$TEST_TABLE (id, name, email, age)
        VALUES (1, 'Updated John', 'john.updated@example.com', 31)
    ON CONFLICT (id)
    DO UPDATE SET
        name = EXCLUDED.name,
        email = EXCLUDED.email,
        age = EXCLUDED.age,
        updated_at = NOW();"

    echo -e "${GREEN}✓ Complex operations completed${NC}"
}

# Check Kafka topics
check_kafka_topics() {
    echo -e "\n${YELLOW}Checking Kafka topics...${NC}"

    docker exec $KAFKA_CONTAINER \
        kafka-topics --list --bootstrap-server localhost:9092 | \
        grep -E "cdc|$TEST_TABLE" || echo "No CDC topics found yet"
}

# Monitor CDC events
monitor_cdc_events() {
    local topic="cdc.$DB_NAME.$TEST_TABLE"

    echo -e "\n${YELLOW}Monitoring CDC events for topic: $topic${NC}"
    echo "Starting consumer (will run for 10 seconds)..."

    consume_kafka $topic 10
}

# Verify application metrics
verify_metrics() {
    echo -e "\n${YELLOW}Checking application metrics...${NC}"

    if curl -s http://localhost:8080/api/v1/cdc/metrics > /dev/null 2>&1; then
        metrics=$(curl -s http://localhost:8080/api/v1/cdc/metrics)
        echo "CDC Metrics:"
        echo "$metrics" | jq '.' 2>/dev/null || echo "$metrics"
    else
        echo "Application metrics endpoint not available"
    fi
}

# Clean up
cleanup() {
    echo -e "\n${YELLOW}Cleaning up...${NC}"

    exec_psql "DROP TABLE IF EXISTS public.$TEST_TABLE CASCADE;"

    echo -e "${GREEN}✓ Test table dropped${NC}"
}

# Main test flow
run_test() {
    echo -e "\n${GREEN}Starting CDC test flow...${NC}\n"

    # Create and populate test table
    create_test_table
    sleep 2

    # Check initial topics
    check_kafka_topics

    # Perform data operations
    insert_test_data
    sleep 2

    update_test_data
    sleep 2

    delete_test_data
    sleep 2

    complex_operations
    sleep 2

    # Monitor events
    monitor_cdc_events

    # Check metrics if application is running
    verify_metrics

    # Show final state
    echo -e "\n${YELLOW}Final table state:${NC}"
    exec_psql "SELECT * FROM public.$TEST_TABLE ORDER BY id;"

    # Cleanup option
    echo -e "\n${YELLOW}Do you want to clean up the test table? (y/n)${NC}"
    read -p "> " cleanup_choice
    if [ "$cleanup_choice" = "y" ]; then
        cleanup
    fi
}

# Performance test
performance_test() {
    echo -e "\n${GREEN}Starting performance test...${NC}\n"

    create_test_table

    echo -e "${YELLOW}Inserting 1000 records...${NC}"
    exec_psql "
    INSERT INTO public.$TEST_TABLE (name, email, age)
    SELECT
        'User_' || generate_series,
        'user_' || generate_series || '@example.com',
        (random() * 50 + 20)::int
    FROM generate_series(1, 1000);"

    echo -e "${GREEN}✓ 1000 records inserted${NC}"

    sleep 5

    echo -e "${YELLOW}Batch update...${NC}"
    exec_psql "UPDATE public.$TEST_TABLE SET updated_at = NOW() WHERE id % 2 = 0;"

    echo -e "${GREEN}✓ Batch update completed${NC}"

    sleep 5

    monitor_cdc_events
    verify_metrics

    cleanup
}

# Interactive menu
show_menu() {
    echo -e "\n${YELLOW}CDC Test Menu:${NC}"
    echo "1) Run complete test flow"
    echo "2) Create test table only"
    echo "3) Insert test data"
    echo "4) Update test data"
    echo "5) Delete test data"
    echo "6) Complex operations test"
    echo "7) Monitor CDC events"
    echo "8) Performance test (1000 records)"
    echo "9) Check Kafka topics"
    echo "10) Verify metrics"
    echo "11) Cleanup"
    echo "12) Exit"
    read -p "Enter your choice [1-12]: " choice
}

# Main execution
main() {
    while true; do
        show_menu
        case $choice in
            1) run_test ;;
            2) create_test_table ;;
            3) insert_test_data ;;
            4) update_test_data ;;
            5) delete_test_data ;;
            6) complex_operations ;;
            7) monitor_cdc_events ;;
            8) performance_test ;;
            9) check_kafka_topics ;;
            10) verify_metrics ;;
            11) cleanup ;;
            12)
                echo "Exiting..."
                exit 0
                ;;
            *)
                echo -e "${RED}Invalid choice${NC}"
                ;;
        esac
    done
}

# Check if containers are running
echo "Checking Docker containers..."

if ! docker ps | grep -q $POSTGRES_CONTAINER; then
    echo -e "${RED}PostgreSQL container is not running${NC}"
    echo "Please start the containers using: docker-compose up -d"
    exit 1
fi

if ! docker ps | grep -q $KAFKA_CONTAINER; then
    echo -e "${RED}Kafka container is not running${NC}"
    echo "Please start the containers using: docker-compose up -d"
    exit 1
fi

echo -e "${GREEN}Containers are running${NC}"

# Run main function
main