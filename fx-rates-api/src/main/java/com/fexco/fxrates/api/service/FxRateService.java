package com.fexco.fxrates.api.service;

import com.fexco.fxrates.common.dto.BatchFxRateRequest;
import com.fexco.fxrates.common.dto.BatchFxRateResponse;
import com.fexco.fxrates.common.dto.FxRateResponse;
import com.fexco.fxrates.common.model.FxRate;

import java.util.List;

/**
 * Service interface for FX Rate operations
 */
public interface FxRateService {

    /**
     * Get current FX rate for a currency pair
     *
     * @param from Source currency
     * @param to   Target currency
     * @return FX rate response
     */
    FxRateResponse getFxRate(String from, String to);

    /**
     * Get batch FX rates for multiple currency pairs
     *
     * @param request Batch request with currency pairs
     * @return Batch response with rates
     */
    BatchFxRateResponse getBatchFxRates(BatchFxRateRequest request);

    /**
     * Get historical FX rates
     *
     * @param from      Source currency
     * @param to        Target currency
     * @param startDate Start date (ISO 8601)
     * @param endDate   End date (ISO 8601)
     * @param limit     Maximum number of records
     * @return List of historical rates
     */
    List<FxRate> getHistoricalRates(String from, String to, String startDate, String endDate, Integer limit);

    /**
     * Get all supported currency pairs
     *
     * @return List of currency pairs
     */
    List<String> getSupportedCurrencyPairs();

    /**
     * Invalidate cache for a currency pair
     *
     * @param currencyPair Currency pair to invalidate
     */
    void invalidateCache(String currencyPair);
}
