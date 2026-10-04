package com.airtribe.prism.controller;

import com.airtribe.prism.domain.GatewayException;
import com.airtribe.prism.domain.PrismErrorResponse;
import com.airtribe.prism.service.RequestLogService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
public class GlobalErrorHandler {

    private final RequestLogService requestLogService;

    public GlobalErrorHandler(RequestLogService requestLogService) {
        this.requestLogService = requestLogService;
    }

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<Map<String, PrismErrorResponse>> handleGatewayException(GatewayException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("error", new PrismErrorResponse(exception.getMessage(), exception.type(), exception.code())));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, WebExchangeBindException.class, ServerWebInputException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, PrismErrorResponse>> handleBadRequest(Exception exception) {
        requestLogService.appendGatewayRejection("<unknown>", null, "invalid_request_error");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", new PrismErrorResponse(exception.getMessage(), "invalid_request_error", "invalid_request_error")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, PrismErrorResponse>> handleGeneric(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", new PrismErrorResponse(exception.getMessage(), "internal_error", "internal_error")));
    }
}
