package com.fexco.fxrates.api.repository;

import com.fexco.fxrates.common.model.FxRate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for FX Rate persistence operations
 */
public interface FxRateRepository {

    /**
     * Find the latest FX rate for a currency pair
     *
     * @param currencyPair Currency pair (e.g., "EURUSD")
     * @return Optional FX rate
     */
    Optional<FxRate> findLatestByCurrencyPair(String currencyPair);

    /**
     * Find historical rates for a currency pair within a date range
     *
     * @param currencyPair Currency pair
     * @param startDate    Start date
     * @param endDate      End date
     * @param limit        Maximum number of records
     * @return List of historical rates
     */
    List<FxRate> findHistoricalRates(String currencyPair, Instant startDate, Instant endDate, Integer limit);

    /**
     * Save an FX rate
     *
     * @param fxRate FX rate to save
     * @return Saved FX rate
     */
    FxRate save(FxRate fxRate);

    /**
     * Find all distinct currency pairs
     *
     * @return List of currency pairs
     */
    List<String> findAllCurrencyPairs();

    /**
     * Delete rates older than specified timestamp
     *
     * @param timestamp Cutoff timestamp
     * @return Number of deleted records
     */
    int deleteOlderThan(Instant timestamp);
}
