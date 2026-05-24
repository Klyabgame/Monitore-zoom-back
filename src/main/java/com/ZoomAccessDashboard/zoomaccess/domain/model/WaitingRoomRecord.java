package com.ZoomAccessDashboard.zoomaccess.domain.model;

import java.time.Instant;

/**
 * Aggregated domain model representing a participant's complete waiting room session.
 * Used to report to the frontend as specified in the backend API spec.
 */
public record WaitingRoomRecord(
    String meetingId,
    String topic,
    String participantName,
    String email,
    Instant waitingRoomEntryTime,
    Instant exitTime,
    Long waitingDurationSeconds,
    String connectionStatus,
    String leaveReason
) {}
