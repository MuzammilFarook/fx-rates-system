package com.fexco.fxrates.ingestion.scheduler;

import com.fexco.fxrates.ingestion.service.RateIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic FX rate ingestion
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "app.ingestion.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class RateIngestionScheduler {

    private final RateIngestionService rateIngestionService;

    @Scheduled(cron = "${app.ingestion.schedule.cron:*/5 * * * * *}")
    public void ingestRates() {
        log.debug("Starting scheduled rate ingestion");

        try {
            long startTime = System.currentTimeMillis();
            rateIngestionService.ingestRatesFromAllProviders();
            long duration = System.currentTimeMillis() - startTime;

            log.info("Completed scheduled rate ingestion in {}ms", duration);
        } catch (Exception e) {
            log.error("Error during scheduled rate ingestion", e);
        }
    }
}
