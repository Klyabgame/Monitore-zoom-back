package com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest;

import com.ZoomAccessDashboard.zoomaccess.domain.model.ZoomAccessRecord;
import com.ZoomAccessDashboard.zoomaccess.domain.port.in.ProcessWebhookUseCase;
import com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest.dto.ZoomCrcResponse;
import com.ZoomAccessDashboard.zoomaccess.infrastructure.input.rest.dto.ZoomPayloadDto;
import com.ZoomAccessDashboard.zoomaccess.infrastructure.security.ZoomSignatureValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * REST Controller that exposes the POST endpoint to receive Zoom webhooks.
 * Implements security validation (HMAC SHA-256) and handles Zoom CRC checks.
 * Uses an in-memory Sink for queueing and buffering webhooks.
 */
@RestController
@RequiredArgsConstructor
public class ZoomWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ZoomWebhookController.class);

    private final ProcessWebhookUseCase processWebhookUseCase;
    private final ZoomSignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    // Thread-safe reactive sink for buffering Zoom webhook records
    private final Sinks.Many<ZoomAccessRecord> webhookSink = Sinks.many().multicast().onBackpressureBuffer();

    // Hot shared flux for broadcasting to multiple SSE clients and the background database/sheet processor
    private Flux<List<ZoomAccessRecord>> sharedWebhookFlux;

    @PostConstruct
    public void initProcessor() {
        log.info("Starting background reactive webhook processor...");
        
        // Share the buffered flux so multiple SSE subscribers receive the exact same batches
        // without triggering separate buffers or database/sheet processes.
        this.sharedWebhookFlux = webhookSink.asFlux()
                .bufferTimeout(20, Duration.ofSeconds(2))
                .share();

        this.sharedWebhookFlux
                .flatMap(records -> processWebhookBatch(records)
                        .onErrorResume(error -> {
                            log.error("Failed to process buffered webhook batch", error);
                            return Mono.empty();
                        })
                )
                .subscribe();
    }

    /**
     * Endpoint to stream real-time attendance updates via Server-Sent Events (SSE).
     * Broadcasts the buffered batches to all connected clients.
     */
    @GetMapping(value = "/api/webhooks/zoom/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<List<ZoomAccessRecord>>> streamWebhookEvents() {
        log.info("New SSE client subscribed to Zoom webhook stream");
        return this.sharedWebhookFlux
                .map(records -> ServerSentEvent.<List<ZoomAccessRecord>>builder()
                        .data(records)
                        .build())
                .doOnCancel(() -> log.info("SSE client disconnected from Zoom webhook stream"))
                .doOnError(error -> log.error("Error in SSE webhook stream", error));
    }

    private Mono<Void> processWebhookBatch(List<ZoomAccessRecord> records) {
        log.info("Processing buffered batch of {} ZoomAccessRecords", records.size());
        return processWebhookUseCase.process(records);
    }

    /**
     * Endpoint to receive and process Zoom webhook events.
     *
     * @param signature the Zoom request signature
     * @param timestamp the Zoom request timestamp
     * @param rawBody   the raw request body string
     * @return 200 OK or 401 Unauthorized, or URL validation payload
     */
    @PostMapping("/api/webhooks/zoom")
    public Mono<ResponseEntity<Object>> handleWebhook(
            @RequestHeader(value = "x-zm-signature", required = false) String signature,
            @RequestHeader(value = "x-zm-request-timestamp", required = false) String timestamp,
            @RequestBody String rawBody
    ) {
        log.info("Received Zoom webhook request. Signature: {}, Timestamp: {}", signature, timestamp);

        return Mono.just(rawBody)
                // 1. Validate signature reactively (validates headers internally)
                .flatMap(body -> signatureValidator.validate(body, signature, timestamp))
                // 2. Log raw payload for debugging (temporary)
                .doOnNext(body -> System.out.println("JSON CRUDO DE ZOOM: " + body))
                // 3. Parse JSON
                .flatMap(body -> Mono.fromCallable(() -> objectMapper.readValue(body, ZoomPayloadDto.class))
                        .onErrorResume(error -> {
                            log.error("Failed to parse Zoom webhook payload JSON", error);
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload"));
                        }))
                // 4. Process: CRC challenge or queue event
                .flatMap(payload -> "endpoint.url_validation".equals(payload.event())
                        ? handleCrcValidation(payload)
                        : queueWebhookEvent(payload)
                );
    }

    private Mono<ResponseEntity<Object>> handleCrcValidation(ZoomPayloadDto payload) {
        log.info("Handling URL validation challenge (CRC)");
        return Mono.justOrEmpty(payload.payload())
                .map(ZoomPayloadDto.PayloadData::plainToken)
                .map(plainToken -> {
                    String encryptedToken = signatureValidator.encryptToken(plainToken);
                    ZoomCrcResponse crcResponse = new ZoomCrcResponse(plainToken, encryptedToken);
                    return ResponseEntity.ok((Object) crcResponse);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("plainToken is missing in CRC challenge request");
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing plainToken"));
                }));
    }

    private Mono<ResponseEntity<Object>> queueWebhookEvent(ZoomPayloadDto payload) {
        return Mono.just(payload)
                .filter(p -> p.payload() != null && p.payload().object() != null)
                .map(this::mapToDomain)
                .doOnNext(record -> {
                    log.debug("Queueing record in reactive sink: {}", record);
                    webhookSink.tryEmitNext(record);
                })
                .map(record -> ResponseEntity.ok().build())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Null payload or object received, skipping");
                    return Mono.just(ResponseEntity.ok().build());
                }));
    }

    private ZoomAccessRecord mapToDomain(ZoomPayloadDto p) {
        var obj = p.payload().object();
        var participant = obj.participant();

        Instant timestamp = (p.eventTs() == null)
                ? Instant.now()
                : ((p.eventTs() < 9999999999L) ? Instant.ofEpochSecond(p.eventTs()) : Instant.ofEpochMilli(p.eventTs()));

        String participantName = (participant != null && participant.userName() != null)
                ? participant.userName() : "Anonymous";
        String participantEmail = (participant != null && participant.email() != null)
                ? participant.email() : "";
        String leaveReason = (participant != null && participant.leaveReason() != null)
                ? participant.leaveReason() : "";

        return new ZoomAccessRecord(
                p.event(),
                obj.id() == null ? "" : obj.id(),
                obj.topic() == null ? "Zoom Meeting" : obj.topic(),
                participantName,
                participantEmail,
                timestamp,
                leaveReason
        );
    }
}
