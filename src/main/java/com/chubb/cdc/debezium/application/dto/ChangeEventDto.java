package com.chubb.cdc.debezium.application.dto;

import com.chubb.cdc.debezium.domain.changecapture.model.ChangeEvent;
import com.chubb.cdc.debezium.domain.changecapture.model.OperationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Data Transfer Object for change events.
 *
 * <p>This DTO maps from the domain ChangeEvent entity to a serializable format
 * suitable for transmission over REST APIs and Kafka topics. It includes Jackson
 * annotations for clean JSON serialization.</p>
 *
 * <p>All fields are immutable and the class is designed for JSON serialization/deserialization.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChangeEventDto(
    @JsonProperty("table")
    TableIdentifierDto table,

    @JsonProperty("operation")
    OperationType operation,

    @JsonProperty("timestamp")
    Instant timestamp,

    @JsonProperty("position")
    CdcPositionDto position,

    @JsonProperty("before")
    Map<String, Object> before,

    @JsonProperty("after")
    Map<String, Object> after,

    @JsonProperty("metadata")
    Map<String, Object> metadata
) {
    /**
     * Converts a domain ChangeEvent to a DTO.
     *
     * @param event the domain change event
     * @return the DTO representation
     */
    public static ChangeEventDto fromDomain(ChangeEvent event) {
        return new ChangeEventDto(
            TableIdentifierDto.fromDomain(event.table()),
            event.operation(),
            event.timestamp(),
            CdcPositionDto.fromDomain(event.position()),
            event.before() != null ? event.before().fields() : null,
            event.after() != null ? event.after().fields() : null,
            event.metadata()
        );
    }

    /**
     * DTO for table identifier.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TableIdentifierDto(
        @JsonProperty("database")
        String database,

        @JsonProperty("schema")
        String schema,

        @JsonProperty("table")
        String table
    ) {
        public static TableIdentifierDto fromDomain(
            com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier identifier
        ) {
            return new TableIdentifierDto(
                identifier.database(),
                identifier.schema(),
                identifier.table()
            );
        }
    }

    /**
     * DTO for CDC position.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CdcPositionDto(
        @JsonProperty("sourcePartition")
        String sourcePartition,

        @JsonProperty("offset")
        Map<String, Object> offset
    ) {
        public static CdcPositionDto fromDomain(
            com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition position
        ) {
            return new CdcPositionDto(
                position.sourcePartition(),
                position.offset()
            );
        }
    }
}
