package com.ZoomAccessDashboard.zoomaccess.domain.port.out;

import com.ZoomAccessDashboard.zoomaccess.domain.model.ZoomAccessRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Output port defining interactions with the persistent spreadsheet store.
 */
public interface SpreadsheetRepository {
    /**
     * Saves a single Zoom access record.
     *
     * @param record the record to save
     * @return Mono<Void> indicating completion
     */
    Mono<Void> saveRecord(ZoomAccessRecord record);

    /**
     * Saves a batch of Zoom access records.
     *
     * @param records the list of records to save
     * @return Mono<Void> indicating completion
     */
    Mono<Void> saveRecords(List<ZoomAccessRecord> records);

    /**
     * Retrieves all Zoom access records stored.
     *
     * @return Flux of all saved ZoomAccessRecords
     */
    Flux<ZoomAccessRecord> findAllRecords();
}
