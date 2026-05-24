package com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest;

import com.ZoomAccessDashboard.zoomaccess.domain.model.PageResponse;
import com.ZoomAccessDashboard.zoomaccess.domain.port.in.GetWaitingRoomRecordsUseCase;
import com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest.dto.WaitingRoomRecordResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST Controller that exposes the query endpoints to read and search waiting room records.
 * Serves the data requested by the dashboard frontend.
 */
@RestController
@RequiredArgsConstructor
public class ZoomWaitingRoomController {

    private static final Logger log = LoggerFactory.getLogger(ZoomWaitingRoomController.class);

    private final GetWaitingRoomRecordsUseCase getWaitingRoomRecordsUseCase;

    /**
     * Retrieves waiting room records based on filter criteria and pagination parameters.
     *
     * @param participantName optional search term for participant name, email, or topic (case-insensitive)
     * @param startDate       optional start date boundary (ISO-8601 string)
     * @param endDate         optional end date boundary (ISO-8601 string)
     * @param status          optional status filter ("Admitted", "Abandoned")
     * @param page            the page number (0-indexed)
     * @param size            the page size
     * @return Mono of PageResponse containing formatted WaitingRoomRecordResponse objects
     */
    @GetMapping("/api/v1/zoom/waiting-room-records")
    public Mono<PageResponse<WaitingRoomRecordResponse>> getWaitingRoomRecords(
            @RequestParam(value = "participantName", required = false) String participantName,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        log.info("GET /api/v1/zoom/waiting-room-records - Filters: name={}, start={}, end={}, status={}, page={}, size={}",
                participantName, startDate, endDate, status, page, size);

        return getWaitingRoomRecordsUseCase.execute(participantName, startDate, endDate, status, page, size)
                .map(pageResult -> {
                    List<WaitingRoomRecordResponse> responseContent = pageResult.content().stream()
                            .map(WaitingRoomRecordResponse::fromDomain)
                            .toList();
                    return PageResponse.of(
                            responseContent,
                            pageResult.totalElements(),
                            pageResult.page(),
                            pageResult.size()
                    );
                });
    }

    /**
     * Retrieves the list of raw Zoom access records logged in the spreadsheet.
     *
     * @return Flux of raw ZoomAccessRecords
     */
    @GetMapping("/api/v1/zoom/raw-records")
    public Flux<com.ZoomAccessDashboard.zoomaccess.domain.model.ZoomAccessRecord> getRawRecords() {
        log.info("GET /api/v1/zoom/raw-records");
        return getWaitingRoomRecordsUseCase.getRawRecords();
    }
}
