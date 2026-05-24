package com.ZoomAccessDashboard.zoomaccess.infrastructure.output.google;

import com.ZoomAccessDashboard.zoomaccess.domain.model.ZoomAccessRecord;
import com.ZoomAccessDashboard.zoomaccess.domain.port.out.SpreadsheetRepository;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Adapter implementing the SpreadsheetRepository port for Google Sheets.
 * Wraps synchronous API calls using Reactor's boundedElastic scheduler.
 * Implemented completely using reactive/functional paradigms (no 'if' or 'for').
 */
@Component
public class GoogleSheetsAdapter implements SpreadsheetRepository {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsAdapter.class);

    @Value("${google.sheets.spreadsheet-id}")
    private String spreadsheetId;

    @Value("${google.sheets.credentials-path}")
    private String credentialsPath;

    @Value("${google.sheets.range:Sheet1}")
    private String range;

    private Sheets sheetsService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("America/Lima"));

    @PostConstruct
    public void init() throws Exception {
        log.info("Initializing Google Sheets API Client");
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        // Avoid if-else via ternary operator
        GoogleCredentials credentials = credentialsPath.startsWith("classpath:")
                ? loadFromClasspath(credentialsPath.substring("classpath:".length()))
                : loadFromFileSystem(credentialsPath);

        this.sheetsService = new Sheets.Builder(
                httpTransport,
                jsonFactory,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("ZoomAccessDashboard")
                .build();
        log.info("Google Sheets API Client successfully initialized");
    }

    private GoogleCredentials loadFromClasspath(String resourcePath) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            // Avoid if check via Optional.orElseThrow
            InputStream stream = java.util.Optional.ofNullable(in)
                    .orElseThrow(() -> new FileNotFoundException("Classpath credentials file not found: " + resourcePath));
            return GoogleCredentials.fromStream(stream)
                    .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        }
    }

    private GoogleCredentials loadFromFileSystem(String path) throws Exception {
        try (InputStream in = new FileInputStream(path)) {
            return GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        }
    }

    @Override
    public Mono<Void> saveRecord(ZoomAccessRecord record) {
        log.debug("Saving ZoomAccessRecord to Google Sheets: {}", record);
        return Mono.fromCallable(() -> {
            String formattedTimestamp = dateFormatter.format(record.timestamp());
            
            // 7-column layout to capture all information:
            // 1. eventName, 2. meetingId, 3. topic, 4. participantName, 5. participantEmail, 6. timestamp, 7. leaveReason
            List<List<Object>> values = List.of(
                    List.of(
                            record.eventName(),
                            record.meetingId(),
                            record.topic(),
                            record.participantName(),
                            record.participantEmail(),
                            formattedTimestamp,
                            record.leaveReason()
                    )
            );

            ValueRange body = new ValueRange().setValues(values);

            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, range + "!A:A", body)
                    .setValueInputOption("USER_ENTERED")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();
            log.info("Successfully appended row for participant: {} to spreadsheet", record.participantName());
            return true;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }

    @Override
    public Mono<Void> saveRecords(List<ZoomAccessRecord> records) {
        return Mono.justOrEmpty(records)
                .filter(list -> !list.isEmpty())
                .flatMap(list -> Mono.fromCallable(() -> {
                    log.debug("Saving batch of {} ZoomAccessRecords to Google Sheets", list.size());
                    List<List<Object>> values = list.stream()
                            .map(record -> List.<Object>of(
                                    record.eventName(),
                                    record.meetingId(),
                                    record.topic(),
                                    record.participantName(),
                                    record.participantEmail(),
                                    dateFormatter.format(record.timestamp()),
                                    record.leaveReason() != null ? record.leaveReason() : ""
                            ))
                            .toList();

                    ValueRange body = new ValueRange().setValues(values);

                    sheetsService.spreadsheets().values()
                            .append(spreadsheetId, range + "!A:A", body)
                            .setValueInputOption("USER_ENTERED")
                            .setInsertDataOption("INSERT_ROWS")
                            .execute();
                    log.info("Successfully appended batch of {} rows to spreadsheet", list.size());
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then());
    }

    @Override
    public Flux<ZoomAccessRecord> findAllRecords() {
        log.debug("Fetching all records from Google Sheets");
        return Mono.fromCallable(() -> {
            // Read A:G to cover all 7 columns
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range + "!A:G")
                    .execute();

            List<List<Object>> values = response.getValues();
            return java.util.Optional.ofNullable(values)
                    .orElse(Collections.emptyList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable)
        .skip(1) // Skip the header row
        .filter(row -> row.size() >= 5)
        .flatMap(row -> {
            String eventName = getSafeString(row, 0);
            String meetingId = getSafeString(row, 1);
            
            boolean hasTopic = row.size() >= 6;
            String topic = hasTopic ? getSafeString(row, 2) : "Zoom Meeting";
            String name = hasTopic ? getSafeString(row, 3) : getSafeString(row, 2);
            String email = hasTopic ? getSafeString(row, 4) : getSafeString(row, 3);
            String tsStr = hasTopic ? getSafeString(row, 5) : getSafeString(row, 4);

            boolean hasLeaveReason = row.size() >= 7;
            String leaveReason = hasLeaveReason ? getSafeString(row, 6) : "";

            return parseInstantWithFallbackReactive(tsStr)
                    .map(timestamp -> new ZoomAccessRecord(eventName, meetingId, topic, name, email, timestamp, leaveReason));
        });
    }

    private Mono<Instant> parseInstantWithFallbackReactive(String tsStr) {
        return Mono.fromCallable(() -> Instant.parse(tsStr))
                .onErrorResume(e -> Mono.fromCallable(() -> {
                    double serial = Double.parseDouble(tsStr);
                    long localMs = (long) ((serial - 25569.0) * 86400.0 * 1000.0);
                    // Excel serial represents local Peru time (UTC-5). Add 5 hours to get the correct UTC Instant.
                    return Instant.ofEpochMilli(localMs + 5 * 3600 * 1000);
                }))
                .onErrorResume(e -> Mono.fromCallable(() ->
                        java.time.LocalDateTime.parse(tsStr, dateFormatter)
                                .atZone(ZoneId.of("America/Lima"))
                                .toInstant()
                ))
                .onErrorReturn(Instant.now());
    }

    private String getSafeString(List<Object> row, int index) {
        return (index < row.size() && row.get(index) != null)
                ? row.get(index).toString()
                : "";
    }
}
