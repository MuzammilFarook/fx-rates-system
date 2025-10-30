package com.fexco.fxrates.common.constant;

/**
 * Constants for Redis caching
 */
public final class CacheConstants {

    private CacheConstants() {
        // Utility class
    }

    public static final String FX_RATE_CACHE_PREFIX = "fx:rate:";
    public static final String FX_RATE_HISTORY_CACHE_PREFIX = "fx:history:";
    public static final String FX_RATE_BATCH_CACHE_PREFIX = "fx:batch:";

    // Cache TTL in seconds
    public static final int FX_RATE_TTL_SECONDS = 5; // 5 seconds for current rates
    public static final int FX_RATE_HISTORY_TTL_SECONDS = 3600; // 1 hour for historical data

    // Pub/Sub channel names
    public static final String FX_RATE_UPDATES_CHANNEL = "fx-rate-updates";
    public static final String FX_RATE_ALERTS_CHANNEL = "fx-rate-alerts";
}
