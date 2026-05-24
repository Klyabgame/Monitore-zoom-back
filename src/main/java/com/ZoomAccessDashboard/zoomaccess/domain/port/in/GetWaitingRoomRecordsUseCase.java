package com.ZoomAccessDashboard.zoomaccess.domain.port.in;

import com.ZoomAccessDashboard.zoomaccess.domain.model.PageResponse;
import com.ZoomAccessDashboard.zoomaccess.domain.model.WaitingRoomRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Input port defining the query usecase for fetching waiting room records.
 */
public interface GetWaitingRoomRecordsUseCase {
    /**
     * Retrieves waiting room records based on filter criteria and pagination parameters.
     *
     * @param participantName optional search term for participant name, email, or topic (case-insensitive)
     * @param startDate       optional start date boundary (ISO-8601 string)
     * @param endDate         optional end date boundary (ISO-8601 string)
     * @param status          optional status filter ("Admitted", "Abandoned")
     * @param page            the page number (0-indexed)
     * @param size            the page size
     * @return Mono of PageResponse containing matching WaitingRoomRecords
     */
    Mono<PageResponse<WaitingRoomRecord>> execute(String participantName, String startDate, String endDate, String status, int page, int size);

    /**
     * Retrieves raw Zoom access records from the spreadsheet without any aggregation.
     *
     * @return Flux of raw ZoomAccessRecords
     */
    Flux<com.ZoomAccessDashboard.zoomaccess.domain.model.ZoomAccessRecord> getRawRecords();
}
