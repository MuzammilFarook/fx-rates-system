package com.fexco.fxrates.ingestion.publisher;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fexco.fxrates.common.constant.EventHubConstants;
import com.fexco.fxrates.common.event.FxRateUpdatedEvent;
import com.fexco.fxrates.common.model.FxRate;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publisher for sending FX rate updates to Azure Event Hubs
 */
@Component
@Slf4j
public class EventHubPublisher {

    private final String connectionString;
    private final String eventHubName;
    private final ObjectMapper objectMapper;

    private EventHubProducerClient producerClient;

    public EventHubPublisher(
            @Value("${azure.eventhub.connection-string:}") String connectionString,
            @Value("${azure.eventhub.topic:fx-rates-updates}") String eventHubName,
            ObjectMapper objectMapper
    ) {
        this.connectionString = connectionString;
        this.eventHubName = eventHubName;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        if (connectionString != null && !connectionString.isEmpty()) {
            log.info("Initializing Event Hub producer for: {}", eventHubName);

            producerClient = new EventHubClientBuilder()
                    .connectionString(connectionString, eventHubName)
                    .buildProducerClient();

            log.info("Event Hub producer initialized successfully");
        } else {
            log.warn("Event Hub connection string not configured. Publisher disabled.");
        }
    }

    /**
     * Publish FX rate updates to Event Hub
     */
    public void publishRateUpdates(List<FxRate> fxRates) {
        if (producerClient == null) {
            log.warn("Event Hub producer not initialized. Skipping publish.");
            return;
        }

        if (fxRates.isEmpty()) {
            log.debug("No rates to publish");
            return;
        }

        try {
            log.info("Publishing {} FX rate updates to Event Hub", fxRates.size());

            EventDataBatch eventDataBatch = producerClient.createBatch();

            for (FxRate rate : fxRates) {
                FxRateUpdatedEvent event = FxRateUpdatedEvent.from(rate, "rate-ingestion-service");

                String eventJson = objectMapper.writeValueAsString(event);
                EventData eventData = new EventData(eventJson);

                // Add headers
                eventData.getProperties().put(EventHubConstants.EVENT_TYPE_HEADER, event.getEventType());
                eventData.getProperties().put(EventHubConstants.CORRELATION_ID_HEADER, event.getEventId());
                eventData.getProperties().put(EventHubConstants.SOURCE_HEADER, event.getSource());

                // Try to add to batch
                if (!eventDataBatch.tryAdd(eventData)) {
                    // Batch is full, send it
                    producerClient.send(eventDataBatch);
                    log.debug("Sent batch of events to Event Hub");

                    // Create new batch and add current event
                    eventDataBatch = producerClient.createBatch();
                    eventDataBatch.tryAdd(eventData);
                }
            }

            // Send remaining events
            if (eventDataBatch.getCount() > 0) {
                producerClient.send(eventDataBatch);
                log.info("Successfully published {} events to Event Hub", fxRates.size());
            }

        } catch (Exception e) {
            log.error("Error publishing to Event Hub", e);
            throw new RuntimeException("Failed to publish FX rate updates", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (producerClient != null) {
            log.info("Closing Event Hub producer");
            producerClient.close();
        }
    }
}
