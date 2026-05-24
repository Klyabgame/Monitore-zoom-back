package com.ZoomAccessDashboard.zoomaccess.domain.model;

import java.time.Instant;

/**
 * Domain model representing a waiting room event from Zoom.
 * Keeps track of events, participants, and meetings.
 */
public record ZoomAccessRecord(
    String eventName,
    String meetingId,
    String topic,
    String participantName,
    String participantEmail,
    Instant timestamp,
    String leaveReason
) {}
