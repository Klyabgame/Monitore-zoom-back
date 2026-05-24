package com.ZoomAccessDashboard.zoomaccess.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Validator to check the integrity and authenticity of Zoom Webhook requests.
 * Uses HMAC-SHA256 signature checking according to Zoom security guidelines.
 */
@Component
public class ZoomSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(ZoomSignatureValidator.class);

    private final String secretToken;

    public ZoomSignatureValidator(@Value("${zoom.secret-token}") String secretToken) {
        this.secretToken = secretToken;
    }

    /**
     * Verifies that the webhook request signature matches the calculated signature reactively.
     *
     * @param payload         the raw JSON request body
     * @param signatureHeader the value of the 'x-zm-signature' HTTP header
     * @param timestampHeader the value of the 'x-zm-request-timestamp' HTTP header
     * @return Mono emitting the payload if valid, or a ResponseStatusException error if invalid
     */
    public Mono<String> validate(String payload, String signatureHeader, String timestampHeader) {
        return Mono.justOrEmpty(payload)
                // 1. Check configuration
                .filter(p -> secretToken != null && !secretToken.isBlank())
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Zoom Secret Token is not configured");
                    return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Zoom Secret Token is not configured"));
                }))
                // 2. Check headers and payload presence
                .filter(p -> signatureHeader != null && timestampHeader != null)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Missing security headers or payload");
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing security headers or payload"));
                }))
                // 3. Parse timestamp
                .flatMap(p -> Mono.fromCallable(() -> Long.parseLong(timestampHeader))
                        .onErrorResume(NumberFormatException.class, e -> {
                            log.warn("Invalid timestamp format: {}", timestampHeader);
                            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid timestamp format"));
                        })
                        // 4. Validate timestamp expiration
                        .filter(requestTimestamp -> Math.abs(Instant.now().getEpochSecond() - requestTimestamp) <= 300)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Webhook request timestamp is expired: requestTs={}, currentTs={}",
                                    timestampHeader, Instant.now().getEpochSecond());
                            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook request timestamp is expired"));
                        }))
                        // 5. Verify signature
                        .filter(requestTimestamp -> checkSignature(p, signatureHeader, timestampHeader))
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Signature mismatch. SignatureHeader: {}, Computed: v0={}",
                                    signatureHeader, new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secretToken).hmacHex("v0:" + timestampHeader + ":" + p));
                            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature validation failed"));
                        }))
                        .map(ignored -> p)
                );
//      return Mono.just(payload); // para testing
    }

    private boolean checkSignature(String payload, String signatureHeader, String timestampHeader) {
        String message = "v0:" + timestampHeader + ":" + payload;
        String computedSignature = "v0=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secretToken).hmacHex(message);
        return computedSignature.equalsIgnoreCase(signatureHeader);
    }

    /**
     * Computes the HMAC-SHA256 of the plainToken received during URL validation.
     * Used for the Zoom CRC (Challenge-Response Check) validation challenge.
     *
     * @param plainToken the plain token sent by Zoom
     * @return hex encoded encrypted token
     */
    public String encryptToken(String plainToken) {
        return java.util.Optional.ofNullable(secretToken)
                .filter(token -> !token.isBlank())
                .map(token -> new HmacUtils(HmacAlgorithms.HMAC_SHA_256, token).hmacHex(plainToken))
                .orElseThrow(() -> new IllegalStateException("Zoom Secret Token is not configured"));
    }
}
