package com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object representing the incoming Zoom Webhook event payload.
 * Modeled using nested Java records for clean, immutable data bindings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZoomPayloadDto(
        @JsonProperty("event") String event,
        @JsonProperty("event_ts") Long eventTs,
        @JsonProperty("payload") PayloadData payload
) {
    /**
     * Inner payload data structure.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PayloadData(
            @JsonProperty("account_id") String accountId,
            @JsonProperty("plainToken") String plainToken,
            @JsonProperty("object") PayloadObject object
    ) {}

    /**
     * Inner object containing meeting details.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PayloadObject(
            @JsonProperty("id") String id,
            @JsonProperty("uuid") String uuid,
            @JsonProperty("host_id") String hostId,
            @JsonProperty("topic") String topic,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("participant") ParticipantDetails participant
    ) {}

    /**
     * Inner object containing participant details.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParticipantDetails(
            @JsonProperty("user_id") String userId,
            @JsonProperty("user_name") String userName,
            @JsonProperty("email") String email,
            @JsonProperty("id") String id,
            @JsonProperty("registrant_id") String registrantId,
            @JsonProperty("leave_reason") String leaveReason
    ) {}
}
