package com.fexco.fxrates.websocket.consumer;

import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fexco.fxrates.common.event.FxRateUpdatedEvent;
import com.fexco.fxrates.websocket.service.SubscriptionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Event Hub consumer for receiving FX rate updates
 */
@Component
@Slf4j
public class EventHubConsumer {

    private final String connectionString;
    private final String eventHubName;
    private final String consumerGroup;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    private EventProcessorClient eventProcessorClient;

    public EventHubConsumer(
            @Value("${azure.eventhub.connection-string:}") String connectionString,
            @Value("${azure.eventhub.topic:fx-rates-updates}") String eventHubName,
            @Value("${azure.eventhub.consumer-group:websocket-service}") String consumerGroup,
            SubscriptionService subscriptionService,
            ObjectMapper objectMapper
    ) {
        this.connectionString = connectionString;
        this.eventHubName = eventHubName;
        this.consumerGroup = consumerGroup;
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        if (connectionString == null || connectionString.isEmpty()) {
            log.warn("Event Hub connection string not configured. Consumer disabled.");
            return;
        }

        log.info("Starting Event Hub consumer for: {} with consumer group: {}", eventHubName, consumerGroup);

        // For production, use Blob Storage for checkpointing
        // For demo, we'll use in-memory checkpointing
        eventProcessorClient = new EventProcessorClientBuilder()
                .consumerGroup(consumerGroup)
                .connectionString(connectionString, eventHubName)
                .processEvent(this::processEvent)
                .processError(this::processError)
                .buildEventProcessorClient();

        eventProcessorClient.start();
        log.info("Event Hub consumer started successfully");
    }

    /**
     * Process incoming events
     */
    private void processEvent(EventContext eventContext) {
        try {
            String eventData = eventContext.getEventData().getBodyAsString();
            log.debug("Received event: {}", eventData);

            // Deserialize event
            FxRateUpdatedEvent event = objectMapper.readValue(eventData, FxRateUpdatedEvent.class);

            // Broadcast to subscribed WebSocket clients
            subscriptionService.broadcastRateUpdate(event);

            // Checkpoint the event
            eventContext.updateCheckpointAsync();

        } catch (Exception e) {
            log.error("Error processing event", e);
        }
    }

    /**
     * Handle errors
     */
    private void processError(ErrorContext errorContext) {
        log.error("Error in Event Hub consumer. Partition: {}, Error: {}",
                errorContext.getPartitionContext().getPartitionId(),
                errorContext.getThrowable().getMessage(),
                errorContext.getThrowable());
    }

    @PreDestroy
    public void stop() {
        if (eventProcessorClient != null) {
            log.info("Stopping Event Hub consumer");
            eventProcessorClient.stop();
        }
    }
}
