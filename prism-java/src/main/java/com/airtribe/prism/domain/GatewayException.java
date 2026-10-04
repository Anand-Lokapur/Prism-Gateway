package com.airtribe.prism.domain;

import org.springframework.http.HttpStatus;

public class GatewayException extends RuntimeException {
    private final HttpStatus status;
    private final String type;
    private final String code;

    public GatewayException(HttpStatus status, String type, String code, String message) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String type() {
        return type;
    }

    public String code() {
        return code;
    }
}
