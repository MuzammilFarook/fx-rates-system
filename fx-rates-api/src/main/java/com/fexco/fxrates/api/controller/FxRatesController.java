package com.fexco.fxrates.api.controller;

import com.fexco.fxrates.api.service.FxRateService;
import com.fexco.fxrates.common.dto.*;
import com.fexco.fxrates.common.model.FxRate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for FX Rates API
 * Provides endpoints for partners to query real-time FX rates
 */
@RestController
@RequestMapping("/rates")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "FX Rates API", description = "Endpoints for querying foreign exchange rates")
public class FxRatesController {

    private final FxRateService fxRateService;

    @Operation(
            summary = "Get FX rate for a currency pair",
            description = "Retrieves the current exchange rate for a specific currency pair. " +
                    "Results are cached for optimal performance."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved rate"),
            @ApiResponse(responseCode = "404", description = "Currency pair not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{from}/{to}")
    public ResponseEntity<FxRateResponse> getFxRate(
            @Parameter(description = "Source currency code (ISO 4217)", example = "EUR")
            @PathVariable
            @Pattern(regexp = "[A-Z]{3}", message = "Currency code must be 3-letter ISO code")
            String from,

            @Parameter(description = "Target currency code (ISO 4217)", example = "USD")
            @PathVariable
            @Pattern(regexp = "[A-Z]{3}", message = "Currency code must be 3-letter ISO code")
            String to
    ) {
        log.info("Received request for FX rate: {} to {}", from, to);

        long startTime = System.currentTimeMillis();
        FxRateResponse response = fxRateService.getFxRate(from, to);
        long duration = System.currentTimeMillis() - startTime;

        response.setLatencyMs(duration);

        log.info("Returned FX rate for {}{} in {}ms (cached: {})",
                from, to, duration, response.getFromCache());

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get batch FX rates",
            description = "Retrieves exchange rates for multiple currency pairs in a single request. " +
                    "Maximum 100 pairs per request."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved rates"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/batch")
    public ResponseEntity<BatchFxRateResponse> getBatchFxRates(
            @Parameter(description = "Request containing list of currency pairs")
            @Valid @RequestBody BatchFxRateRequest request
    ) {
        log.info("Received batch request for {} currency pairs", request.getCurrencyPairs().size());

        long startTime = System.currentTimeMillis();
        BatchFxRateResponse response = fxRateService.getBatchFxRates(request);
        long duration = System.currentTimeMillis() - startTime;

        response.setLatencyMs(duration);

        log.info("Returned batch FX rates: {}/{} successful in {}ms",
                response.getTotalSuccessful(), response.getTotalRequested(), duration);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get historical FX rates",
            description = "Retrieves historical exchange rates for a currency pair. " +
                    "Supports filtering by date range."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved historical rates"),
            @ApiResponse(responseCode = "404", description = "No historical data found"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @GetMapping("/history/{from}/{to}")
    public ResponseEntity<List<FxRate>> getHistoricalRates(
            @Parameter(description = "Source currency code", example = "EUR")
            @PathVariable @Pattern(regexp = "[A-Z]{3}") String from,

            @Parameter(description = "Target currency code", example = "USD")
            @PathVariable @Pattern(regexp = "[A-Z]{3}") String to,

            @Parameter(description = "Start date (ISO 8601)", example = "2024-01-01T00:00:00Z")
            @RequestParam(required = false) String startDate,

            @Parameter(description = "End date (ISO 8601)", example = "2024-01-31T23:59:59Z")
            @RequestParam(required = false) String endDate,

            @Parameter(description = "Maximum number of records", example = "100")
            @RequestParam(defaultValue = "100") Integer limit
    ) {
        log.info("Received historical rates request for {}{} (start: {}, end: {}, limit: {})",
                from, to, startDate, endDate, limit);

        List<FxRate> historicalRates = fxRateService.getHistoricalRates(
                from, to, startDate, endDate, limit
        );

        log.info("Returned {} historical rates for {}{}", historicalRates.size(), from, to);

        return ResponseEntity.ok(historicalRates);
    }

    @Operation(
            summary = "Get all supported currency pairs",
            description = "Returns a list of all currency pairs supported by the system"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved currency pairs")
    })
    @GetMapping("/pairs")
    public ResponseEntity<List<String>> getSupportedCurrencyPairs() {
        log.info("Received request for supported currency pairs");

        List<String> pairs = fxRateService.getSupportedCurrencyPairs();

        log.info("Returned {} supported currency pairs", pairs.size());

        return ResponseEntity.ok(pairs);
    }

    @Operation(
            summary = "Health check endpoint",
            description = "Returns the health status of the FX Rates API service"
    )
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("FX Rates API is healthy");
    }
}
