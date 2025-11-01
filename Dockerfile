# Multi-stage build for Debezium CDC Application

# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S cdc && adduser -S cdc -G cdc

# Create directories for data and config
RUN mkdir -p /data/offsets /config /logs && \
    chown -R cdc:cdc /data /config /logs /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change to non-root user
USER cdc

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
