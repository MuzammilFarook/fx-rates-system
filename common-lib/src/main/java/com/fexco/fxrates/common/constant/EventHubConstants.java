package com.fexco.fxrates.common.constant;

/**
 * Constants for Azure Event Hubs
 */
public final class EventHubConstants {

    private EventHubConstants() {
        // Utility class
    }

    // Event Hub names
    public static final String FX_RATES_UPDATES_TOPIC = "fx-rates-updates";
    public static final String FX_RATES_AUDIT_TOPIC = "fx-rates-audit";
    public static final String FX_RATES_INGESTION_TOPIC = "fx-rates-ingestion";

    // Consumer groups
    public static final String WEBSOCKET_CONSUMER_GROUP = "websocket-service";
    public static final String CACHE_UPDATER_CONSUMER_GROUP = "cache-updater";
    public static final String PERSISTENCE_CONSUMER_GROUP = "persistence-service";

    // Event properties
    public static final String EVENT_TYPE_HEADER = "event-type";
    public static final String CORRELATION_ID_HEADER = "correlation-id";
    public static final String SOURCE_HEADER = "source";
}
