package com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object representing the response to Zoom's Challenge-Response Check (CRC).
 */
public record ZoomCrcResponse(
        @JsonProperty("plainToken") String plainToken,
        @JsonProperty("encryptedToken") String encryptedToken
) {}
