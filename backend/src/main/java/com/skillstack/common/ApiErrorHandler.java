package com.skillstack.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);
    private final ApiErrorWriter errors;

    public ApiErrorHandler(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorWriter.ApiError> handleApi(ApiException exception, HttpServletRequest request) {
        return errors.response(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorWriter.ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var fieldErrors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()));
        return errors.response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request contains invalid values", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorWriter.ApiError> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request contains invalid values", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorWriter.ApiError> handleMalformedJson(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is not valid JSON", request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiErrorWriter.ApiError> handleMalformedParameter(Exception exception, HttpServletRequest request) {
        return errors.response(HttpStatus.BAD_REQUEST, "MALFORMED_PARAMETER", "A request parameter has an invalid value", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorWriter.ApiError> handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Database constraint rejected request requestId={} path={}", errors.requestId(request), request.getRequestURI());
        return errors.response(HttpStatus.CONFLICT, "GRAPH_CONFLICT", "The requested graph change conflicts with existing data", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorWriter.ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        var requestId = errors.requestId(request);
        log.error("Unexpected request failure requestId={} path={}", requestId, request.getRequestURI(), exception);
        return errors.response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }
}
