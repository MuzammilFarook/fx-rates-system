package com.fexco.fxrates.api.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.DirectConnectionConfig;
import com.azure.cosmos.GatewayConnectionConfig;
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

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    @Value("${azure.cosmos.consistency-level:SESSION}")
    private String consistencyLevel;

    @Bean
    public CosmosClient cosmosClient() {
        log.info("Creating Cosmos DB client for endpoint: {}", endpoint);

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .directMode(DirectConnectionConfig.getDefaultConfig())
                .consistencyLevel(com.azure.cosmos.ConsistencyLevel.valueOf(consistencyLevel))
                .buildClient();
    }
}
