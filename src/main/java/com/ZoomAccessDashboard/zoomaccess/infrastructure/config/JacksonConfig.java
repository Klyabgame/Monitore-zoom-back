package com.ZoomAccessDashboard.zoomaccess.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to customize the Jackson ObjectMapper.
 * Exposes a custom ObjectMapper bean with FAIL_ON_UNKNOWN_PROPERTIES set to false,
 * ensuring all incoming JSON payloads with unexpected fields are parsed without errors.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
