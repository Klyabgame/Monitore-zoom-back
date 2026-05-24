package com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest.dto;

import com.ZoomAccessDashboard.zoomaccess.domain.model.WaitingRoomRecord;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Data Transfer Object representing the waiting room record returned to the frontend.
 * Conforms strictly to the frontend API specification.
 */
public record WaitingRoomRecordResponse(
        String meetingId,
        String topic,
        String participantName,
        String email,
        String waitingRoomEntryTime,
        String exitTime,
        Long waitingDurationSeconds,
        String connectionStatus,
        String leaveReason
) {
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    /**
     * Maps a domain WaitingRoomRecord to a WaitingRoomRecordResponse.
     *
     * @param record the domain model record
     * @return the response DTO record
     */
    public static WaitingRoomRecordResponse fromDomain(WaitingRoomRecord record) {
        String entryTimeStr = record.waitingRoomEntryTime() != null 
                ? ISO_FORMATTER.format(record.waitingRoomEntryTime()) 
                : "";
        String exitTimeStr = record.exitTime() != null 
                ? ISO_FORMATTER.format(record.exitTime()) 
                : null;

        return new WaitingRoomRecordResponse(
                record.meetingId(),
                record.topic(),
                record.participantName(),
                record.email(),
                entryTimeStr,
                exitTimeStr,
                record.waitingDurationSeconds(),
                record.connectionStatus(),
                record.leaveReason()
        );
    }
}
