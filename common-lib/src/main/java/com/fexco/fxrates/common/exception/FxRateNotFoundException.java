package com.fexco.fxrates.common.exception;

/**
 * Exception thrown when requested FX rate is not found
 */
public class FxRateNotFoundException extends RuntimeException {

    private final String currencyPair;

    public FxRateNotFoundException(String currencyPair) {
        super(String.format("FX rate not found for currency pair: %s", currencyPair));
        this.currencyPair = currencyPair;
    }

    public FxRateNotFoundException(String from, String to) {
        this(from + to);
    }

    public String getCurrencyPair() {
        return currencyPair;
    }
}
