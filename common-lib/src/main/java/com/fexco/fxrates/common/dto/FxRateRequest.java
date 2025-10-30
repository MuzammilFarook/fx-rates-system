package com.fexco.fxrates.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for retrieving FX rates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateRequest {

    @NotBlank(message = "From currency is required")
    @Pattern(regexp = "[A-Z]{3}", message = "From currency must be 3-letter ISO code")
    private String fromCurrency;

    @NotBlank(message = "To currency is required")
    @Pattern(regexp = "[A-Z]{3}", message = "To currency must be 3-letter ISO code")
    private String toCurrency;

    /**
     * Optional: Include historical data
     */
    private Boolean includeHistory;

    /**
     * Optional: Get rate as of specific timestamp
     */
    private String asOfTimestamp;
}
