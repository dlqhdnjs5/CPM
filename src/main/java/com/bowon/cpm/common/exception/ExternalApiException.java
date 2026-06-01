package com.bowon.cpm.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ExternalApiException extends CpmException {

    private final String provider;
    private final Integer httpStatus;

    public ExternalApiException(String provider, String message) {
        super("EXTERNAL_API_ERROR", message, HttpStatus.BAD_GATEWAY);
        this.provider = provider;
        this.httpStatus = null;
    }

    public ExternalApiException(String provider, String message, Integer httpStatus) {
        super("EXTERNAL_API_ERROR", message, HttpStatus.BAD_GATEWAY);
        this.provider = provider;
        this.httpStatus = httpStatus;
    }
}

