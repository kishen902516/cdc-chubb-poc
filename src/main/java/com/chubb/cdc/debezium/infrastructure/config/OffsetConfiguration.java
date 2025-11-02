package com.chubb.cdc.debezium.infrastructure.config;

import com.chubb.cdc.debezium.infrastructure.persistence.offset.OffsetRepositoryAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for offset storage components.
 * 
 * This configuration ensures proper initialization of offset storage
 * components with the correct file path properties.
 */
@Configuration
public class OffsetConfiguration {

    /**
     * Creates the OffsetRepositoryAdapter bean with properly injected file path.
     * 
     * @param offsetFilePath Path to the offset storage file
     * @return OffsetRepositoryAdapter instance
     */
    @Bean
    public OffsetRepositoryAdapter offsetRepositoryAdapter(
        @Value("${cdc.offset.file-path:data/offsets/cdc-offsets.dat}") String offsetFilePath
    ) {
        return new OffsetRepositoryAdapter(offsetFilePath);
    }
}