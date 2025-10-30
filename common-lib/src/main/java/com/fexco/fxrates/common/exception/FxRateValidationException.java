package com.fexco.fxrates.common.exception;

/**
 * Exception thrown when FX rate validation fails
 */
public class FxRateValidationException extends RuntimeException {

    public FxRateValidationException(String message) {
        super(message);
    }

    public FxRateValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
