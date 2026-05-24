package com.ZoomAccessDashboard.zoomaccess.domain.port.in;

import com.ZoomAccessDashboard.zoomaccess.domain.model.ZoomAccessRecord;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Input port defining the usecase for processing incoming Zoom webhooks.
 */
public interface ProcessWebhookUseCase {
    /**
     * Processes a batch of incoming Zoom access records.
     *
     * @param records the list of records to process
     * @return Mono<Void> representing the completion of processing
     */
    Mono<Void> process(List<ZoomAccessRecord> records);
}
