package com.fexco.fxrates.ingestion.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Azure Cosmos DB
 */
@Configuration
@Slf4j
public class CosmosDbConfig {

    @Value("${azure.cosmos.endpoint:}")
    private String endpoint;

    @Value("${azure.cosmos.key:}")
    private String key;

    @Value("${azure.cosmos.database:fxrates}")
    private String databaseName;

    @Value("${azure.cosmos.container:rates}")
    private String containerName;

    @Bean
    public CosmosClient cosmosClient() {
        if (endpoint == null || endpoint.isEmpty()) {
            log.warn("Cosmos DB endpoint not configured. Cosmos DB client will not be initialized.");
            return null;
        }

        log.info("Initializing Cosmos DB client for endpoint: {}", endpoint);

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .buildClient();
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosClient cosmosClient) {
        if (cosmosClient == null) {
            log.warn("Cosmos DB client not available. Container will not be initialized.");
            return null;
        }

        log.info("Getting Cosmos DB container: {} in database: {}", containerName, databaseName);

        CosmosDatabase database = cosmosClient.getDatabase(databaseName);
        CosmosContainer container = database.getContainer(containerName);

        log.info("Cosmos DB container initialized successfully");
        return container;
    }
}
