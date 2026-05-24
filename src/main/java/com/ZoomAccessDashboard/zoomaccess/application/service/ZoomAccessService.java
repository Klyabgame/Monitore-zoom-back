package com.ZoomAccessDashboard.zoomaccess.application.service;

import com.ZoomAccessDashboard.zoomaccess.domain.model.PageResponse;
import com.ZoomAccessDashboard.zoomaccess.domain.model.WaitingRoomRecord;
import com.ZoomAccessDashboard.zoomaccess.domain.model.ZoomAccessRecord;
import com.ZoomAccessDashboard.zoomaccess.domain.port.in.GetWaitingRoomRecordsUseCase;
import com.ZoomAccessDashboard.zoomaccess.domain.port.in.ProcessWebhookUseCase;
import com.ZoomAccessDashboard.zoomaccess.domain.port.out.SpreadsheetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementing the use cases for Zoom waiting room logging.
 * Combines raw events into consolidated waiting room records and handles querying with filters.
 * Re-implemented without any 'if' or 'for' loops, following a purely functional style.
 */
@Service
@RequiredArgsConstructor
public class ZoomAccessService implements ProcessWebhookUseCase, GetWaitingRoomRecordsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ZoomAccessService.class);

    private final SpreadsheetRepository spreadsheetRepository;

    @Override
    public Mono<Void> process(List<ZoomAccessRecord> records) {
        log.info("Processing Zoom webhook batch of size {} in application layer", records.size());
        return spreadsheetRepository.saveRecords(records);
    }

    @Override
    public Mono<PageResponse<WaitingRoomRecord>> execute(String participantName, String startDate, String endDate, String status, int page, int size) {
        log.info("Retrieving waiting room records individually with filters - Name: {}, StartDate: {}, EndDate: {}, Status: {}, Page: {}, Size: {}",
                participantName, startDate, endDate, status, page, size);

        Mono<Optional<Instant>> startMono = parseInstantReactive(startDate);
        Mono<Optional<Instant>> endMono = parseInstantReactive(endDate);

        return Mono.zip(startMono, endMono)
                .flatMap(tuple -> {
                    Optional<Instant> startOpt = tuple.getT1();
                    Optional<Instant> endOpt = tuple.getT2();

                    return spreadsheetRepository.findAllRecords()
                            .collectList()
                            .map(records -> {
                                List<WaitingRoomRecord> allFiltered = records.stream()
                                        .map(r -> new WaitingRoomRecord(
                                                r.meetingId(),
                                                r.topic(),
                                                r.participantName(),
                                                r.participantEmail(),
                                                r.timestamp(),
                                                null,
                                                0L,
                                                r.eventName(),
                                                r.leaveReason()
                                        ))
                                        .filter(record -> matchesParticipantName(record, participantName))
                                        .filter(record -> matchesDateRange(record, startOpt, endOpt))
                                        .filter(record -> matchesStatus(record, status))
                                        .sorted(Comparator.comparing(WaitingRoomRecord::waitingRoomEntryTime, Comparator.nullsLast(Comparator.reverseOrder())))
                                        .toList();

                                int totalElements = allFiltered.size();
                                int fromIndex = Math.min(page * size, totalElements);
                                int toIndex = Math.min(fromIndex + size, totalElements);
                                List<WaitingRoomRecord> pagedContent = allFiltered.subList(fromIndex, toIndex);

                                return PageResponse.of(pagedContent, totalElements, page, size);
                            });
                });
    }

    @Override
    public Flux<com.ZoomAccessDashboard.zoomaccess.domain.model.ZoomAccessRecord> getRawRecords() {
        log.info("Retrieving raw Zoom access events from Google Sheets");
        return spreadsheetRepository.findAllRecords();
    }

    private List<WaitingRoomRecord> aggregateRecords(List<ZoomAccessRecord> records) {
        // Group by meetingId + participant key (email if available, else name) to trace a single user's path
        Map<String, List<ZoomAccessRecord>> grouped = Optional.ofNullable(records)
                .orElse(Collections.emptyList())
                .stream()
                .collect(Collectors.groupingBy(r -> {
                    String userKey = (r.participantEmail() != null && !r.participantEmail().isBlank())
                            ? r.participantEmail()
                            : r.participantName();
                    return r.meetingId() + ":::" + userKey;
                }));

        List<WaitingRoomRecord> result = grouped.values().stream()
                .flatMap(userEvents -> {
                    List<ZoomAccessRecord> sortedEvents = userEvents.stream()
                            .sorted(Comparator.comparing(ZoomAccessRecord::timestamp))
                            .toList();

                    WaitingRoomSessionState initial = new WaitingRoomSessionState(List.of(), null);
                    WaitingRoomSessionState reduced = sortedEvents.stream()
                            .reduce(initial,
                                    WaitingRoomSessionState::addEvent,
                                    (s1, s2) -> s1);

                    return reduced.finalizeState().completedRecords().stream();
                })
                .collect(Collectors.toCollection(ArrayList::new));

        // Sort overall results by entry time descending (most recent first)
        result.sort(Comparator.comparing(WaitingRoomRecord::waitingRoomEntryTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private static WaitingRoomRecord createRecord(ZoomAccessRecord joinEvent, Instant exitTime, String status) {
        Instant entryTime = joinEvent.timestamp();
        long durationSeconds = (exitTime != null && entryTime != null)
                ? Duration.between(entryTime, exitTime).toSeconds()
                : 0L;

        return new WaitingRoomRecord(
                joinEvent.meetingId(),
                joinEvent.topic(),
                joinEvent.participantName(),
                joinEvent.participantEmail(),
                entryTime,
                exitTime,
                durationSeconds,
                status,
                joinEvent.leaveReason()
        );
    }

    private boolean matchesParticipantName(WaitingRoomRecord record, String query) {
        return query == null || query.isBlank() ||
                (record.participantName() != null && record.participantName().toLowerCase().contains(query.toLowerCase())) ||
                (record.email() != null && record.email().toLowerCase().contains(query.toLowerCase())) ||
                (record.topic() != null && record.topic().toLowerCase().contains(query.toLowerCase()));
    }

    private boolean matchesDateRange(WaitingRoomRecord record, Optional<Instant> startOpt, Optional<Instant> endOpt) {
        Instant recordTime = record.waitingRoomEntryTime();
        return recordTime == null || (
                startOpt.map(start -> !recordTime.isBefore(start)).orElse(true) &&
                endOpt.map(end -> !recordTime.isAfter(end)).orElse(true)
        );
    }

    private Mono<Optional<Instant>> parseInstantReactive(String dateStr) {
        return Mono.justOrEmpty(dateStr)
                .filter(s -> !s.isBlank())
                .flatMap(s -> Mono.fromCallable(() -> Instant.parse(s))
                        .onErrorResume(DateTimeParseException.class, e -> Mono.fromCallable(() -> 
                                java.time.LocalDateTime.parse(s)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toInstant()
                        ))
                        .map(Optional::of)
                        .onErrorResume(e -> {
                            log.warn("Failed to parse date filter: {}", s);
                            return Mono.just(Optional.empty());
                        })
                )
                .defaultIfEmpty(Optional.empty());
    }

    private boolean matchesStatus(WaitingRoomRecord record, String status) {
        return status == null || status.isBlank() || "all".equalsIgnoreCase(status) || status.equalsIgnoreCase(record.connectionStatus());
    }

    /**
     * Immutable state-machine accumulator for functional folding of ZoomAccessRecords into WaitingRoomRecords.
     */
    private record WaitingRoomSessionState(
            List<WaitingRoomRecord> completedRecords,
            ZoomAccessRecord activeJoin
    ) {
        public WaitingRoomSessionState addEvent(ZoomAccessRecord event) {
            String eventName = event.eventName();

            return switch (eventName) {
                case "meeting.participant_joined_waiting_room" -> new WaitingRoomSessionState(
                        activeJoin == null
                                ? completedRecords
                                : appendToList(completedRecords, createRecord(activeJoin, null, "Abandoned")),
                        event
                );
                case "meeting.participant_joined" -> new WaitingRoomSessionState(
                        appendToList(completedRecords, createRecord(
                                activeJoin == null ? event : activeJoin,
                                event.timestamp(),
                                "Admitted"
                        )),
                        null
                );
                case "meeting.participant_left_waiting_room" -> new WaitingRoomSessionState(
                        appendToList(completedRecords, createRecord(
                                activeJoin == null ? event : activeJoin,
                                event.timestamp(),
                                "Abandoned"
                        )),
                        null
                );
                default -> this;
            };
        }

        public WaitingRoomSessionState finalizeState() {
            return activeJoin == null
                    ? this
                    : new WaitingRoomSessionState(
                    appendToList(completedRecords, createRecord(activeJoin, null, "Waiting")),
                    null
            );
        }

        private static List<WaitingRoomRecord> appendToList(List<WaitingRoomRecord> list, WaitingRoomRecord record) {
            List<WaitingRoomRecord> newList = new ArrayList<>(list);
            newList.add(record);
            return newList;
        }
    }
}
