package com.mlb.portfolio.exception;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ResponseEntity<ProblemDetail> problem(HttpStatus code, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code, detail);
        body.setTitle(title);
        body.setType(URI.create("about:blank"));
        return ResponseEntity.status(code).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Please check the highlighted fields.");
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> errors.putIfAbsent(err.getField(),
            err.getDefaultMessage() == null ? "Invalid value" : err.getDefaultMessage()));
        if (response.getBody() != null) response.getBody().setProperty("errors", errors);
        return response;
    }
    @ExceptionHandler({ HttpMessageNotReadableException.class, ConstraintViolationException.class })
    public ResponseEntity<ProblemDetail> badRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request content or parameters are invalid.");
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> mediaType(Exception ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type", "Send application/json.");
    }
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests. Please try again later.");
        detail.setTitle("Rate limited");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detail);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> expected(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.BAD_REQUEST;
        return problem(status, status.getReasonPhrase(),
            ex.getReason() == null ? "Request could not be completed." : ex.getReason());
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> database(DataAccessException ex) {
        log.error("Database request failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", "Please retry later.");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unexpected API failure", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", "Please retry later.");
    }
}

