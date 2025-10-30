package com.fexco.fxrates.api.service;

import com.fexco.fxrates.api.repository.FxRateRepository;
import com.fexco.fxrates.common.constant.CacheConstants;
import com.fexco.fxrates.common.dto.BatchFxRateRequest;
import com.fexco.fxrates.common.dto.BatchFxRateResponse;
import com.fexco.fxrates.common.dto.FxRateResponse;
import com.fexco.fxrates.common.exception.FxRateNotFoundException;
import com.fexco.fxrates.common.model.FxRate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of FX Rate Service with caching and resilience patterns
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FxRateServiceImpl implements FxRateService {

    private final FxRateRepository fxRateRepository;
    private final RedisTemplate<String, FxRate> redisTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;

    @Override
    @Cacheable(value = "fx-rates", key = "#from + #to", unless = "#result == null")
    @CircuitBreaker(name = "cosmosdb", fallbackMethod = "getFxRateFallback")
    @Retry(name = "cosmosdb")
    public FxRateResponse getFxRate(String from, String to) {
        log.debug("Fetching FX rate for {}/{}", from, to);

        String currencyPair = from + to;

        // Try Redis first
        FxRate cachedRate = getCachedRate(currencyPair);
        if (cachedRate != null) {
            log.debug("Cache hit for {}", currencyPair);
            return FxRateResponse.success(cachedRate, true);
        }

        // Fallback to Cosmos DB
        log.debug("Cache miss for {}. Fetching from Cosmos DB", currencyPair);
        FxRate rate = fxRateRepository.findLatestByCurrencyPair(currencyPair)
                .orElseThrow(() -> new FxRateNotFoundException(currencyPair));

        // Update cache
        cacheRate(currencyPair, rate);

        return FxRateResponse.success(rate, false);
    }

    @Override
    @CircuitBreaker(name = "cosmosdb", fallbackMethod = "getBatchFxRatesFallback")
    public BatchFxRateResponse getBatchFxRates(BatchFxRateRequest request) {
        log.debug("Fetching batch FX rates for {} pairs", request.getCurrencyPairs().size());

        Map<String, FxRate> ratesMap = new HashMap<>();
        List<String> failedPairs = new ArrayList<>();

        for (String pair : request.getCurrencyPairs()) {
            try {
                // Try cache first
                FxRate cachedRate = getCachedRate(pair);
                if (cachedRate != null) {
                    ratesMap.put(pair, cachedRate);
                    continue;
                }

                // Fallback to database
                Optional<FxRate> rate = fxRateRepository.findLatestByCurrencyPair(pair);
                if (rate.isPresent()) {
                    ratesMap.put(pair, rate.get());
                    cacheRate(pair, rate.get());
                } else {
                    failedPairs.add(pair);
                }
            } catch (Exception e) {
                log.error("Error fetching rate for pair: {}", pair, e);
                failedPairs.add(pair);
            }
        }

        return BatchFxRateResponse.builder()
                .rates(ratesMap)
                .failedPairs(failedPairs)
                .totalRequested(request.getCurrencyPairs().size())
                .totalSuccessful(ratesMap.size())
                .totalFailed(failedPairs.size())
                .retrievedAt(Instant.now())
                .build();
    }

    @Override
    @CircuitBreaker(name = "cosmosdb")
    public List<FxRate> getHistoricalRates(String from, String to, String startDate, String endDate, Integer limit) {
        log.debug("Fetching historical rates for {}/{}", from, to);

        String currencyPair = from + to;

        Instant start = startDate != null ? Instant.parse(startDate) : Instant.now().minusSeconds(86400);
        Instant end = endDate != null ? Instant.parse(endDate) : Instant.now();

        return fxRateRepository.findHistoricalRates(currencyPair, start, end, limit);
    }

    @Override
    public List<String> getSupportedCurrencyPairs() {
        log.debug("Fetching supported currency pairs");

        // Get from cache if available
        String cacheKey = "supported:pairs";
        Set<String> cachedPairs = stringRedisTemplate.opsForSet().members(cacheKey);

        if (cachedPairs != null && !cachedPairs.isEmpty()) {
            return new ArrayList<>(cachedPairs);
        }

        // Fallback to database
        List<String> pairs = fxRateRepository.findAllCurrencyPairs();

        // Cache for 1 hour
        if (!pairs.isEmpty()) {
            stringRedisTemplate.opsForSet().add(cacheKey, pairs.toArray(new String[0]));
            stringRedisTemplate.expire(cacheKey, 1, TimeUnit.HOURS);
        }

        return pairs;
    }

    @Override
    @CacheEvict(value = "fx-rates", key = "#currencyPair")
    public void invalidateCache(String currencyPair) {
        log.info("Invalidating cache for {}", currencyPair);
        String cacheKey = CacheConstants.FX_RATE_CACHE_PREFIX + currencyPair;
        redisTemplate.delete(cacheKey);
    }

    /**
     * Helper method to get cached rate from Redis
     */
    private FxRate getCachedRate(String currencyPair) {
        try {
            String cacheKey = CacheConstants.FX_RATE_CACHE_PREFIX + currencyPair;
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Error reading from cache for {}: {}", currencyPair, e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to cache rate in Redis
     */
    private void cacheRate(String currencyPair, FxRate rate) {
        try {
            String cacheKey = CacheConstants.FX_RATE_CACHE_PREFIX + currencyPair;
            redisTemplate.opsForValue().set(
                    cacheKey,
                    rate,
                    CacheConstants.FX_RATE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("Cached rate for {} with TTL {}s", currencyPair, CacheConstants.FX_RATE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("Error writing to cache for {}: {}", currencyPair, e.getMessage());
        }
    }

    /**
     * Fallback method for circuit breaker
     */
    private FxRateResponse getFxRateFallback(String from, String to, Exception ex) {
        log.error("Circuit breaker triggered for {}/{}. Reason: {}", from, to, ex.getMessage());

        // Try to return stale data from cache
        String currencyPair = from + to;
        FxRate cachedRate = getCachedRate(currencyPair);

        if (cachedRate != null) {
            log.info("Returning stale data from cache for {}", currencyPair);
            FxRateResponse response = FxRateResponse.success(cachedRate, true);
            response.setMessage("Service degraded - returning cached data");
            return response;
        }

        throw new FxRateNotFoundException(currencyPair);
    }

    /**
     * Fallback method for batch requests
     */
    private BatchFxRateResponse getBatchFxRatesFallback(BatchFxRateRequest request, Exception ex) {
        log.error("Circuit breaker triggered for batch request. Reason: {}", ex.getMessage());

        return BatchFxRateResponse.builder()
                .rates(new HashMap<>())
                .failedPairs(request.getCurrencyPairs())
                .totalRequested(request.getCurrencyPairs().size())
                .totalSuccessful(0)
                .totalFailed(request.getCurrencyPairs().size())
                .retrievedAt(Instant.now())
                .build();
    }
}
