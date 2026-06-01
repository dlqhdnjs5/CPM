package com.bowon.cpm.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CpmException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CpmException(String message) {
        super(message);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.code = "CPM_ERROR";
    }

    public CpmException(String code, String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.code = code;
    }
}

