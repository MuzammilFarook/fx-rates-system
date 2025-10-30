package com.fexco.fxrates.api.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fexco.fxrates.common.model.FxRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cosmos DB implementation of FX Rate Repository
 */
@Repository
@Slf4j
public class CosmosDbFxRateRepository implements FxRateRepository {

    private final CosmosContainer container;

    public CosmosDbFxRateRepository(
            CosmosClient cosmosClient,
            @Value("${azure.cosmos.database}") String databaseName,
            @Value("${azure.cosmos.container}") String containerName
    ) {
        CosmosDatabase database = cosmosClient.getDatabase(databaseName);
        this.container = database.getContainer(containerName);
        log.info("Initialized Cosmos DB repository for database: {}, container: {}", databaseName, containerName);
    }

    @Override
    public Optional<FxRate> findLatestByCurrencyPair(String currencyPair) {
        log.debug("Finding latest rate for currency pair: {}", currencyPair);

        String query = "SELECT TOP 1 * FROM c WHERE c.currencyPair = @currencyPair ORDER BY c.timestamp DESC";

        SqlQuerySpec querySpec = new SqlQuerySpec(query)
                .setParameters(List.of(new SqlParameter("@currencyPair", currencyPair)));

        CosmosPagedIterable<FxRate> items = container.queryItems(
                querySpec,
                new CosmosQueryRequestOptions(),
                FxRate.class
        );

        return items.stream().findFirst();
    }

    @Override
    public List<FxRate> findHistoricalRates(String currencyPair, Instant startDate, Instant endDate, Integer limit) {
        log.debug("Finding historical rates for {} between {} and {}", currencyPair, startDate, endDate);

        String query = "SELECT TOP @limit * FROM c " +
                "WHERE c.currencyPair = @currencyPair " +
                "AND c.timestamp >= @startDate " +
                "AND c.timestamp <= @endDate " +
                "ORDER BY c.timestamp DESC";

        SqlQuerySpec querySpec = new SqlQuerySpec(query)
                .setParameters(List.of(
                        new SqlParameter("@currencyPair", currencyPair),
                        new SqlParameter("@startDate", startDate.toString()),
                        new SqlParameter("@endDate", endDate.toString()),
                        new SqlParameter("@limit", limit)
                ));

        CosmosPagedIterable<FxRate> items = container.queryItems(
                querySpec,
                new CosmosQueryRequestOptions(),
                FxRate.class
        );

        return items.stream().collect(Collectors.toList());
    }

    @Override
    public FxRate save(FxRate fxRate) {
        log.debug("Saving FX rate: {}", fxRate.getCurrencyPair());

        // Set timestamps
        if (fxRate.getCreatedAt() == null) {
            fxRate.setCreatedAt(Instant.now());
        }
        fxRate.setUpdatedAt(Instant.now());

        // Generate ID if not present
        if (fxRate.getId() == null) {
            fxRate.setId(generateId(fxRate));
        }

        container.createItem(fxRate);
        log.debug("Saved FX rate with ID: {}", fxRate.getId());

        return fxRate;
    }

    @Override
    public List<String> findAllCurrencyPairs() {
        log.debug("Finding all currency pairs");

        String query = "SELECT DISTINCT VALUE c.currencyPair FROM c";

        SqlQuerySpec querySpec = new SqlQuerySpec(query);

        CosmosPagedIterable<String> items = container.queryItems(
                querySpec,
                new CosmosQueryRequestOptions(),
                String.class
        );

        return items.stream().collect(Collectors.toList());
    }

    @Override
    public int deleteOlderThan(Instant timestamp) {
        log.info("Deleting rates older than {}", timestamp);

        String query = "SELECT c.id, c.currencyPair FROM c WHERE c.timestamp < @timestamp";

        SqlQuerySpec querySpec = new SqlQuerySpec(query)
                .setParameters(List.of(new SqlParameter("@timestamp", timestamp.toString())));

        List<FxRate> ratesToDelete = container.queryItems(
                querySpec,
                new CosmosQueryRequestOptions(),
                FxRate.class
        ).stream().collect(Collectors.toList());

        int deletedCount = 0;
        for (FxRate rate : ratesToDelete) {
            try {
                container.deleteItem(rate.getId(), new com.azure.cosmos.models.PartitionKey(rate.getCurrencyPair()), null);
                deletedCount++;
            } catch (Exception e) {
                log.error("Error deleting item with ID: {}", rate.getId(), e);
            }
        }

        log.info("Deleted {} old rates", deletedCount);
        return deletedCount;
    }

    /**
     * Generate a unique ID for an FX rate
     */
    private String generateId(FxRate fxRate) {
        return String.format("%s_%s_%d",
                fxRate.getCurrencyPair(),
                fxRate.getSource(),
                fxRate.getTimestamp().toEpochMilli()
        );
    }
}
