package com.fexco.fxrates.common.exception;

/**
 * Exception thrown when external FX provider fails
 */
public class ExternalProviderException extends RuntimeException {

    private final String providerName;

    public ExternalProviderException(String providerName, String message) {
        super(String.format("External provider '%s' error: %s", providerName, message));
        this.providerName = providerName;
    }

    public ExternalProviderException(String providerName, String message, Throwable cause) {
        super(String.format("External provider '%s' error: %s", providerName, message), cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
