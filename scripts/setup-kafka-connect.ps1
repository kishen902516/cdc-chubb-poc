# Setup Kafka Connect with Debezium - PowerShell Version
# This script automates the Kafka Connect setup process for Windows

$ErrorActionPreference = "Stop"

Write-Host "===================================" -ForegroundColor Cyan
Write-Host "Kafka Connect Setup Script" -ForegroundColor Cyan
Write-Host "===================================" -ForegroundColor Cyan

# Configuration
$KAFKA_CONNECT_URL = "http://localhost:8083"
$POSTGRES_CONNECTOR_CONFIG = "connectors\postgres-connector.json"

# Function to check if service is ready
function Check-Service {
    param(
        [string]$ServiceName,
        [string]$Url,
        [int]$MaxAttempts = 30
    )

    Write-Host -NoNewline "Checking $ServiceName..."
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 404) {
                Write-Host "OK" -ForegroundColor Green
                return $true
            }
        }
        catch {
            Write-Host -NoNewline "."
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "FAILED" -ForegroundColor Red
    return $false
}

# Function to create Kafka Connect container configuration
function Create-KafkaConnect {
    Write-Host "Creating Kafka Connect container configuration..."

    $config = @'
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
'@

    Set-Content -Path "docker-compose-connect.yml" -Value $config
    Write-Host "Kafka Connect configuration created" -ForegroundColor Green
}

# Function to start services
function Start-Services {
    Write-Host "Starting infrastructure services..."

    # Start base services
    docker-compose up -d

    # Wait for Kafka to be ready
    Check-Service -ServiceName "Kafka" -Url "http://localhost:8090"

    # Start Kafka Connect
    docker-compose -f docker-compose-connect.yml up -d

    # Wait for Kafka Connect to be ready
    Write-Host "Waiting for Kafka Connect to initialize (this may take a minute)..."
    Start-Sleep -Seconds 30
    Check-Service -ServiceName "Kafka Connect" -Url $KAFKA_CONNECT_URL
}

# Function to create connector configurations
function Create-ConnectorConfigs {
    Write-Host "Creating connector configurations..."

    if (-not (Test-Path -Path "connectors")) {
        New-Item -ItemType Directory -Path "connectors" | Out-Null
    }

    # PostgreSQL Connector
    $postgresConfig = @'
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
    "transforms.route.regex": "([^.]+)\.([^.]+)\.([^.]+)",
    "transforms.route.replacement": "cdc.$1.$3"
  }
}
'@

    # MySQL Connector
    $mysqlConfig = @'
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
'@

    Set-Content -Path "connectors\postgres-connector.json" -Value $postgresConfig
    Set-Content -Path "connectors\mysql-connector.json" -Value $mysqlConfig

    Write-Host "Connector configurations created" -ForegroundColor Green
}

# Function to deploy connector
function Deploy-Connector {
    param(
        [string]$ConnectorFile
    )

    $connectorJson = Get-Content -Path $ConnectorFile -Raw | ConvertFrom-Json
    $connectorName = $connectorJson.name

    Write-Host "Deploying connector: $connectorName"

    # Check if connector already exists
    try {
        $existingConnector = Invoke-RestMethod -Uri "$KAFKA_CONNECT_URL/connectors/$connectorName" -Method Get
        Write-Host "Connector $connectorName already exists. Updating..." -ForegroundColor Yellow

        $configJson = $connectorJson.config | ConvertTo-Json -Depth 10
        Invoke-RestMethod -Uri "$KAFKA_CONNECT_URL/connectors/$connectorName/config" `
            -Method Put `
            -ContentType "application/json" `
            -Body $configJson
    }
    catch {
        Write-Host "Creating new connector: $connectorName"
        $body = Get-Content -Path $ConnectorFile -Raw
        Invoke-RestMethod -Uri "$KAFKA_CONNECT_URL/connectors" `
            -Method Post `
            -ContentType "application/json" `
            -Body $body
    }

    # Check status
    Start-Sleep -Seconds 2
    try {
        $status = Invoke-RestMethod -Uri "$KAFKA_CONNECT_URL/connectors/$connectorName/status" -Method Get
        if ($status.connector.state -eq "RUNNING") {
            Write-Host "✓ Connector $connectorName is RUNNING" -ForegroundColor Green
        } else {
            Write-Host "✗ Connector $connectorName status: $($status.connector.state)" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "Could not get connector status" -ForegroundColor Red
    }
}

# Function to list connectors
function List-Connectors {
    Write-Host "`nInstalled Connectors:" -ForegroundColor Yellow
    try {
        $connectors = Invoke-RestMethod -Uri "$KAFKA_CONNECT_URL/connectors" -Method Get
        if ($connectors) {
            $connectors | ForEach-Object { Write-Host "  - $_" }
        } else {
            Write-Host "No connectors found"
        }
    }
    catch {
        Write-Host "Could not list connectors"
    }
}

# Function to show connector status
function Show-Status {
    Write-Host "`nConnector Status:" -ForegroundColor Yellow
    try {
        $connectors = Invoke-RestMethod -Uri "$KAFKA_CONNECT_URL/connectors" -Method Get
        foreach ($connector in $connectors) {
            $status = Invoke-RestMethod -Uri "$KAFKA_CONNECT_URL/connectors/$connector/status" -Method Get
            Write-Host "  $connector : $($status.connector.state)"
        }
    }
    catch {
        Write-Host "Could not get connector status"
    }
}

# Main menu
function Show-Menu {
    Write-Host "`nWhat would you like to do?" -ForegroundColor Yellow
    Write-Host "1) Full setup (create configs, start services, deploy connectors)"
    Write-Host "2) Start services only"
    Write-Host "3) Deploy PostgreSQL connector"
    Write-Host "4) Deploy MySQL connector"
    Write-Host "5) List connectors"
    Write-Host "6) Show connector status"
    Write-Host "7) Stop all services"
    Write-Host "8) Exit"
    $choice = Read-Host "Enter your choice [1-8]"
    return $choice
}

# Main execution
function Main {
    while ($true) {
        $choice = Show-Menu
        switch ($choice) {
            1 {
                Create-KafkaConnect
                Create-ConnectorConfigs
                Start-Services
                $deployChoice = Read-Host "`nDeploy connectors? (y/n)"
                if ($deployChoice -eq "y") {
                    Deploy-Connector -ConnectorFile "connectors\postgres-connector.json"
                }
                List-Connectors
                Show-Status
            }
            2 {
                Start-Services
            }
            3 {
                if (-not (Test-Path -Path "connectors\postgres-connector.json")) {
                    Create-ConnectorConfigs
                }
                Deploy-Connector -ConnectorFile "connectors\postgres-connector.json"
            }
            4 {
                if (-not (Test-Path -Path "connectors\mysql-connector.json")) {
                    Create-ConnectorConfigs
                }
                Deploy-Connector -ConnectorFile "connectors\mysql-connector.json"
            }
            5 {
                List-Connectors
            }
            6 {
                Show-Status
            }
            7 {
                Write-Host "Stopping all services..."
                docker-compose -f docker-compose-connect.yml down
                docker-compose down
                Write-Host "All services stopped" -ForegroundColor Green
            }
            8 {
                Write-Host "Exiting..."
                exit
            }
            default {
                Write-Host "Invalid choice" -ForegroundColor Red
            }
        }
    }
}

# Check prerequisites
Write-Host "Checking prerequisites..."

# Check Docker
try {
    $dockerVersion = docker --version
    Write-Host "Docker found: $dockerVersion" -ForegroundColor Green
}
catch {
    Write-Host "Docker is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

# Run main function
Main