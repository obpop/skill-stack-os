package com.skillstack.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Component
public class ApiErrorWriter {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorWriter.class);
    private final ObjectMapper mapper;

    public ApiErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record ApiError(String code, String message, OffsetDateTime timestamp, String requestId,
                           Map<String, String> fieldErrors) {}

    public ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                             HttpServletRequest request) {
        return response(status, code, message, request, null);
    }

    public ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                             HttpServletRequest request, Map<String, String> fieldErrors) {
        var requestId = requestId(request);
        return ResponseEntity.status(status).header("X-Request-Id", requestId)
                .body(error(code, message, requestId, fieldErrors));
    }

    public void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                      String code, String message) throws IOException {
        var error = error(code, message, requestId(request), null);
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setHeader("X-Request-Id", error.requestId());
        log.warn("Request rejected code={} requestId={} path={}", code, error.requestId(), request.getRequestURI());
        mapper.writeValue(response.getOutputStream(), error);
    }

    public String requestId(HttpServletRequest request) {
        var supplied = request.getHeader("X-Request-Id");
        if (supplied != null && supplied.length() <= 80 && supplied.matches("[A-Za-z0-9._:-]+")) return supplied;
        return UUID.randomUUID().toString();
    }

    private ApiError error(String code, String message, String requestId, Map<String, String> fieldErrors) {
        return new ApiError(code, message, OffsetDateTime.now(ZoneOffset.UTC), requestId, fieldErrors);
    }
}
