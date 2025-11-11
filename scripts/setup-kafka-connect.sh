#!/bin/bash

# Setup Kafka Connect with Debezium
# This script automates the Kafka Connect setup process

set -e

echo "==================================="
echo "Kafka Connect Setup Script"
echo "==================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
KAFKA_CONNECT_URL="http://localhost:8083"
POSTGRES_CONNECTOR_CONFIG="connectors/postgres-connector.json"

# Function to check if service is ready
check_service() {
    local service_name=$1
    local url=$2
    local max_attempts=30
    local attempt=1

    echo -n "Checking $service_name..."
    while [ $attempt -le $max_attempts ]; do
        if curl -s -o /dev/null -w "%{http_code}" $url | grep -q "200\|404"; then
            echo -e "${GREEN}OK${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    echo -e "${RED}FAILED${NC}"
    return 1
}

# Function to create Kafka Connect container
create_kafka_connect() {
    echo "Creating Kafka Connect container configuration..."

    cat > docker-compose-connect.yml <<'EOF'
version: '3.8'

services:
  kafka-connect:
    image: confluentinc/cp-kafka-connect:7.5.0
    container_name: kafka-connect
    depends_on:
      - kafka
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
      CONNECT_LOG4J_LOGGERS: "io.debezium=INFO"
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
EOF

    echo -e "${GREEN}Kafka Connect configuration created${NC}"
}

# Function to start services
start_services() {
    echo "Starting infrastructure services..."

    # Start base services
    docker-compose up -d

    # Wait for Kafka to be ready
    check_service "Kafka" "http://localhost:8090"

    # Start Kafka Connect
    docker-compose -f docker-compose-connect.yml up -d

    # Wait for Kafka Connect to be ready
    echo "Waiting for Kafka Connect to initialize (this may take a minute)..."
    sleep 30
    check_service "Kafka Connect" "$KAFKA_CONNECT_URL"
}

# Function to create connector configurations
create_connector_configs() {
    echo "Creating connector configurations..."

    mkdir -p connectors

    # PostgreSQL Connector
    cat > connectors/postgres-connector.json <<'EOF'
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
    "transforms": "route",
    "transforms.route.type": "org.apache.kafka.connect.transforms.RegexRouter",
    "transforms.route.regex": "([^.]+)\\.([^.]+)\\.([^.]+)",
    "transforms.route.replacement": "cdc.$1.$3"
  }
}
EOF

    # MySQL Connector
    cat > connectors/mysql-connector.json <<'EOF'
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
    "snapshot.mode": "when_needed"
  }
}
EOF

    echo -e "${GREEN}Connector configurations created${NC}"
}

# Function to deploy connector
deploy_connector() {
    local connector_file=$1
    local connector_name=$(cat $connector_file | grep -oP '"name"\s*:\s*"\K[^"]+')

    echo "Deploying connector: $connector_name"

    # Check if connector already exists
    if curl -s $KAFKA_CONNECT_URL/connectors/$connector_name | grep -q "\"name\""; then
        echo -e "${YELLOW}Connector $connector_name already exists. Updating...${NC}"
        curl -X PUT -H "Content-Type: application/json" \
            --data @$connector_file \
            $KAFKA_CONNECT_URL/connectors/$connector_name/config
    else
        echo "Creating new connector: $connector_name"
        curl -X POST -H "Content-Type: application/json" \
            --data @$connector_file \
            $KAFKA_CONNECT_URL/connectors
    fi

    # Check status
    sleep 2
    status=$(curl -s $KAFKA_CONNECT_URL/connectors/$connector_name/status | grep -oP '"state"\s*:\s*"\K[^"]+' | head -1)
    if [ "$status" = "RUNNING" ]; then
        echo -e "${GREEN}✓ Connector $connector_name is RUNNING${NC}"
    else
        echo -e "${RED}✗ Connector $connector_name status: $status${NC}"
    fi
}

# Function to list connectors
list_connectors() {
    echo -e "\n${YELLOW}Installed Connectors:${NC}"
    curl -s $KAFKA_CONNECT_URL/connectors | jq -r '.[]' 2>/dev/null || echo "No connectors found"
}

# Function to show connector status
show_status() {
    echo -e "\n${YELLOW}Connector Status:${NC}"
    for connector in $(curl -s $KAFKA_CONNECT_URL/connectors | jq -r '.[]' 2>/dev/null); do
        status=$(curl -s $KAFKA_CONNECT_URL/connectors/$connector/status | jq -r '.connector.state')
        echo "  $connector: $status"
    done
}

# Main menu
show_menu() {
    echo -e "\n${YELLOW}What would you like to do?${NC}"
    echo "1) Full setup (create configs, start services, deploy connectors)"
    echo "2) Start services only"
    echo "3) Deploy PostgreSQL connector"
    echo "4) Deploy MySQL connector"
    echo "5) List connectors"
    echo "6) Show connector status"
    echo "7) Stop all services"
    echo "8) Exit"
    read -p "Enter your choice [1-8]: " choice
}

# Main execution
main() {
    while true; do
        show_menu
        case $choice in
            1)
                create_kafka_connect
                create_connector_configs
                start_services
                echo -e "\n${YELLOW}Deploy connectors? (y/n)${NC}"
                read -p "> " deploy_choice
                if [ "$deploy_choice" = "y" ]; then
                    deploy_connector "connectors/postgres-connector.json"
                fi
                list_connectors
                show_status
                ;;
            2)
                start_services
                ;;
            3)
                if [ ! -f "connectors/postgres-connector.json" ]; then
                    create_connector_configs
                fi
                deploy_connector "connectors/postgres-connector.json"
                ;;
            4)
                if [ ! -f "connectors/mysql-connector.json" ]; then
                    create_connector_configs
                fi
                deploy_connector "connectors/mysql-connector.json"
                ;;
            5)
                list_connectors
                ;;
            6)
                show_status
                ;;
            7)
                echo "Stopping all services..."
                docker-compose -f docker-compose-connect.yml down
                docker-compose down
                echo -e "${GREEN}All services stopped${NC}"
                ;;
            8)
                echo "Exiting..."
                exit 0
                ;;
            *)
                echo -e "${RED}Invalid choice${NC}"
                ;;
        esac
    done
}

# Check prerequisites
echo "Checking prerequisites..."
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed${NC}"
    exit 1
fi

if ! command -v curl &> /dev/null; then
    echo -e "${RED}curl is not installed${NC}"
    exit 1
fi

# Run main function
main