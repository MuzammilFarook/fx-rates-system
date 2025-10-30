# Rate Ingestion Service - Deep Dive

## 🎯 Service Purpose

**rate-ingestion-service** is the **DATA SOURCE** for the entire FX rates system. It's a **WRITE-ONLY** service that fetches rates from external providers and distributes them.

### What It Does ✅
- Fetches FX rates from external providers (Reuters, Bloomberg, etc.)
- Validates and enriches rate data
- **Publishes to Event Hubs** for real-time distribution
- **Writes to Cosmos DB** (for historical storage)
- Runs on a schedule (every 5 seconds by default)
- Implements circuit breakers for provider failures
- Monitors ingestion metrics

### What It Does NOT Do ❌
- ❌ Does NOT serve REST API requests (that's fx-rates-api)
- ❌ Does NOT read from Event Hubs (that's websocket-service)
- ❌ Does NOT push WebSocket updates (that's websocket-service)
- ❌ Does NOT cache in Redis (that's fx-rates-api)

---

## 📊 Data Flow

### Ingestion Flow (Every 5 Seconds)

```
1. Spring @Scheduled Task Triggers
   ↓
2. RateIngestionScheduler (Orchestrator)
   ↓
3. RateIngestionService
   ├─→ ExternalFxProviderClient
   │   ├─→ Fetch from External API (Circuit Breaker)
   │   └─→ Convert to FxRate objects
   ↓
4. RateValidationService
   ├─→ Validate rate data
   ├─→ Check for excessive deviation
   ├─→ Enrich with metadata
   └─→ Set confidence scores
   ↓
5. CosmosDbWriter
   ├─→ Write to Cosmos DB for historical storage ✅
   └─→ Track RU consumption
   ↓
6. EventHubPublisher
   ├─→ Convert to FxRateUpdatedEvent
   ├─→ Batch events
   └─→ Publish to Azure Event Hubs ✅
```

### Circuit Breaker Fallback (Provider Down)

```
Scheduled Task Triggers
   ↓
ExternalFxProviderClient.fetchRates()
   ↓
External Provider UNREACHABLE ❌
   ↓
Circuit Breaker OPENS
   ↓
fallbackMethod: fetchRatesFallback()
   ↓
Return empty list (skip this cycle)
   ↓
Log error, publish failure event
   ↓
Wait 30s before retrying (circuit breaker wait duration)
```

---

## 🏗️ Architecture Components

### 1. Scheduler Layer

**File:** `scheduler/RateIngestionScheduler.java`

**Responsibility:** Triggers ingestion on a schedule

```java
@Component
@ConditionalOnProperty("app.ingestion.schedule.enabled")
public class RateIngestionScheduler {

    @Scheduled(cron = "${app.ingestion.schedule.cron:*/5 * * * * *}")
    public void ingestRates() {
        rateIngestionService.ingestRatesFromAllProviders();
    }
}
```

**Configuration:**
```yaml
app:
  ingestion:
    schedule:
      enabled: true
      cron: "*/5 * * * * *"  # Every 5 seconds
```

**How it works:**
- ✅ Runs every 5 seconds (configurable)
- ✅ Can be disabled via config (`enabled: false`)
- ✅ Logs execution time
- ✅ Catches exceptions (won't crash the scheduler)

---

### 2. Service Layer

**File:** `service/RateIngestionService.java`

**Key Method:** `ingestRatesFromAllProviders()`

```java
@Service
public class RateIngestionService {

    public void ingestRatesFromAllProviders() {
        // 1. Generate unique batch ID
        String batchId = UUID.randomUUID().toString();

        // 2. Fetch rates from external provider
        List<FxRate> rates = providerClient.fetchRates(currencyPairs);

        // 3. Validate and enrich
        List<FxRate> validatedRates = validationService.validateRates(rates);

        // 4. Publish to Event Hub
        eventHubPublisher.publishRateUpdates(validatedRates);

        // 5. Publish ingestion event (for monitoring)
        publishIngestionEvent(batchId, "SUCCESS", validatedRates.size());
    }
}
```

**Ingestion Metrics:**
```java
FxRateIngestionEvent {
    eventId: "uuid",
    batchId: "uuid",
    providerName: "ExternalFXProvider",
    rateCount: 8,
    currencyPairs: ["EURUSD", "GBPUSD", ...],
    ingestedAt: "2024-01-15T10:30:00Z",
    status: "SUCCESS",
    processingTimeMs: 1234
}
```

**Configuration (application.yml:28-80):**
```yaml
app:
  ingestion:
    currency-pairs:
      - EURUSD
      - GBPUSD
      - USDJPY
      - AUDUSD
      - USDCAD
      - EURGBP
      - EURJPY
      - GBPJPY
```

---

### 3. External Provider Client

**File:** `client/ExternalFxProviderClient.java`

**Responsibility:** Fetch rates from external APIs

**Example API Call:**
```java
@CircuitBreaker(name = "externalProvider", fallbackMethod = "fetchRatesFallback")
@Retry(name = "externalProvider")
public List<FxRate> fetchRates(List<String> currencyPairs) {
    // Call external provider API
    Map<String, Object> response = webClient
        .get()
        .uri("EUR")  // Fetch EUR base rates
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(Duration.ofSeconds(5))
        .block();

    return convertToFxRates(response, currencyPairs);
}
```

**Provider Response Example:**
```json
{
  "base": "EUR",
  "date": "2024-01-15",
  "rates": {
    "USD": 1.0850,
    "GBP": 0.8575,
    "JPY": 156.42,
    "AUD": 1.6234,
    "CAD": 1.4523
  }
}
```

**Cross-Rate Calculation:**

For currency pairs like **GBPUSD** (not in the response directly):

```java
private Double calculateRate(String from, String to, Map<String, Double> rates) {
    if (from.equals("EUR")) {
        return rates.get(to);  // EURUSD = 1.0850
    } else if (to.equals("EUR")) {
        return 1.0 / rates.get(from);  // USDEUR = 1 / 1.0850
    } else {
        // GBPUSD = EUR/GBP ÷ EUR/USD
        // GBPUSD = 1.0850 ÷ 0.8575 = 1.2653
        return rates.get(to) / rates.get(from);
    }
}
```

**Mock Bid/Ask Generation (ExternalFxProviderClient.java:95-96):**
```java
bid = rate × 0.9995  // 0.05% below mid rate
ask = rate × 1.0005  // 0.05% above mid rate
```

**Resilience Configuration (application.yml:31-46):**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      externalProvider:
        sliding-window-size: 10
        failure-rate-threshold: 50      # Open after 50% failures
        wait-duration-in-open-state: 30s # Wait 30s before retry

  retry:
    instances:
      externalProvider:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2  # 1s, 2s, 4s
```

**What this means:**
- If 5 out of 10 calls fail → Circuit opens
- Wait 30 seconds before retrying
- When retrying, try 3 times with exponential backoff

---

### 4. Validation Service

**File:** `service/RateValidationService.java`

**Responsibility:** Validate and enrich rates before publishing

**Validation Checks:**

1. **Basic Validation**
   ```java
   - Rate > 0 ✅
   - Currency pair length = 6 ✅
   - Timestamp not null ✅
   ```

2. **Deviation Check**
   ```java
   // Check if rate changed more than 5% from previous value
   if (percentChange > 5.0%) {
       log.warn("Excessive deviation!");
       rate.setConfidenceScore(0.7);  // Lower confidence
   }
   ```

3. **Enrichment**
   ```java
   - Set default confidence score: 0.95
   - Set TTL: 5 seconds
   - Add metadata
   ```

**Example Validation:**

```java
// Previous rate: EURUSD = 1.0850
// New rate:      EURUSD = 1.1400  (5.07% increase!)

// Validation result:
{
  "currencyPair": "EURUSD",
  "rate": 1.1400,
  "confidenceScore": 0.7,  // ← Lowered from 0.95
  "validationWarning": "Excessive deviation detected"
}
```

**Configuration (application.yml:83-85):**
```yaml
app:
  ingestion:
    validation:
      enabled: true
      max-deviation-percent: 5.0
```

---

### 5. Event Hub Publisher

**File:** `publisher/EventHubPublisher.java`

**Responsibility:** Publish validated rates to Azure Event Hubs

**Publishing Flow:**

```java
@Component
public class EventHubPublisher {

    private EventHubProducerClient producerClient;

    @PostConstruct
    public void initialize() {
        producerClient = new EventHubClientBuilder()
            .connectionString(connectionString, eventHubName)
            .buildProducerClient();
    }

    public void publishRateUpdates(List<FxRate> fxRates) {
        EventDataBatch batch = producerClient.createBatch();

        for (FxRate rate : fxRates) {
            // Convert to event
            FxRateUpdatedEvent event = FxRateUpdatedEvent.from(rate);

            // Serialize to JSON
            String json = objectMapper.writeValueAsString(event);
            EventData eventData = new EventData(json);

            // Add headers
            eventData.getProperties().put("EventType", "FxRateUpdated");
            eventData.getProperties().put("CorrelationId", event.getEventId());

            // Add to batch
            if (!batch.tryAdd(eventData)) {
                producerClient.send(batch);  // Send full batch
                batch = producerClient.createBatch();  // Create new batch
                batch.tryAdd(eventData);
            }
        }

        // Send remaining events
        producerClient.send(batch);
    }
}
```

**Event Payload Example:**

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "FxRateUpdated",
  "source": "rate-ingestion-service",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "data": {
    "id": "EURUSD_ExternalFXProvider_1705315800000",
    "currencyPair": "EURUSD",
    "rate": 1.0850,
    "bid": 1.0845,
    "ask": 1.0855,
    "timestamp": "2024-01-15T10:30:00.000Z",
    "source": "ExternalFXProvider",
    "confidenceScore": 0.95
  }
}
```

**Event Headers:**

| Header | Value | Purpose |
|--------|-------|---------|
| EventType | FxRateUpdated | Filter events by type |
| CorrelationId | UUID | Trace events across services |
| Source | rate-ingestion-service | Identify publisher |

**Batching Strategy (application.yml:88-90):**
```yaml
app:
  ingestion:
    eventhub:
      batch-size: 100           # Max events per batch
      flush-interval-ms: 1000   # Force send after 1s
```

---

## 🔧 Dependencies Explained

### Required Dependencies ✅

1. **spring-boot-starter-web** (pom.xml:44)
   - Provides REST endpoints for health checks
   - Actuator support

2. **spring-boot-starter-webflux** (pom.xml:52)
   - WebClient for calling external APIs
   - Non-blocking HTTP client

3. **azure-messaging-eventhubs** (pom.xml:58) ← **CRITICAL**
   - Publishes rate updates to Event Hubs
   - Used by EventHubPublisher

4. **resilience4j-spring-boot3** (pom.xml:65)
   - Circuit breakers for external provider calls
   - Retry logic
   - Fallback methods

5. **spring-boot-starter-actuator** (pom.xml:48)
   - Health checks
   - Metrics (ingestion count, errors, etc.)

### NOT Needed ❌

1. ~~**spring-data-redis**~~ - This service doesn't cache
2. ~~**azure-cosmos**~~ - Will add later for Cosmos DB writes
3. ~~**azure-messaging-eventhubs**~~ in consumer mode - We only PUBLISH

---

## 📝 Configuration Files

### application.yml Breakdown

**Server Configuration:**
```yaml
server:
  port: 8081  # Different from fx-rates-api (8080)
```

**Event Hub Configuration:**
```yaml
azure:
  eventhub:
    namespace: ${EVENTHUB_NAMESPACE:}
    connection-string: ${EVENTHUB_CONNECTION_STRING:}
    topic: fx-rates-updates
```

**Scheduling:**
```yaml
app:
  ingestion:
    schedule:
      enabled: true
      cron: "*/5 * * * * *"  # Every 5 seconds
```

**External Provider:**
```yaml
app:
  ingestion:
    providers:
      - name: ExternalFXProvider
        url: https://api.exchangerate-api.com/v4/latest/
        apiKey: ${EXTERNAL_FX_PROVIDER_API_KEY:}
        timeout: 5000
```

### Environment Variables Needed

```bash
# Required
EVENTHUB_CONNECTION_STRING=Endpoint=sb://...;SharedAccessKeyName=...;SharedAccessKey=...
EVENTHUB_NAMESPACE=fexco-eventhub
EVENTHUB_TOPIC=fx-rates-updates

# External Provider
EXTERNAL_FX_PROVIDER_URL=https://api.exchangerate-api.com/v4/latest/
EXTERNAL_FX_PROVIDER_API_KEY=your-api-key

# Optional (monitoring)
APPINSIGHTS_INSTRUMENTATIONKEY=your-key
```

---

## 🚀 Running the Service

### Option 1: Maven

```bash
cd rate-ingestion-service

# Build
mvn clean package

# Run
mvn spring-boot:run

# Or run JAR
java -jar target/rate-ingestion-service-1.0.0-SNAPSHOT.jar
```

### Option 2: Docker

```bash
# Build image
docker build -t rate-ingestion-service:latest -f rate-ingestion-service/Dockerfile .

# Run container
docker run -p 8081:8081 \
  -e EVENTHUB_CONNECTION_STRING=... \
  -e EVENTHUB_NAMESPACE=fexco-eventhub \
  -e EXTERNAL_FX_PROVIDER_URL=https://api.exchangerate-api.com/v4/latest/ \
  rate-ingestion-service:latest
```

### Option 3: Docker Compose

```bash
docker-compose up rate-ingestion-service
```

**What you should see in logs:**

```
2024-01-15 10:30:00 - Starting Rate Ingestion Service
2024-01-15 10:30:05 - Starting scheduled rate ingestion
2024-01-15 10:30:05 - Fetching rates from ExternalFXProvider for 8 pairs
2024-01-15 10:30:06 - Successfully converted 8 rates
2024-01-15 10:30:06 - Validated 8 out of 8 rates
2024-01-15 10:30:06 - Publishing 8 FX rate updates to Event Hub
2024-01-15 10:30:06 - Successfully published 8 events to Event Hub
2024-01-15 10:30:06 - Completed scheduled rate ingestion in 1234ms
```

---

## 🧪 Testing the Service

### 1. Health Check

```bash
curl http://localhost:8081/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" },
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "externalProvider": "CLOSED"
      }
    }
  }
}
```

### 2. Check Scheduled Tasks

```bash
curl http://localhost:8081/actuator/scheduledtasks
```

**Response:**
```json
{
  "cron": [
    {
      "runnable": {
        "target": "RateIngestionScheduler.ingestRates"
      },
      "expression": "*/5 * * * * *"
    }
  ]
}
```

### 3. Monitor Metrics

```bash
curl http://localhost:8081/actuator/metrics
```

**Custom Metrics:**
- `ingestion.rates.fetched` - Total rates fetched
- `ingestion.rates.validated` - Total validated
- `ingestion.rates.published` - Total published
- `ingestion.errors.count` - Total errors

### 4. View Logs

```bash
# Follow logs
docker logs -f rate-ingestion-service

# Expected output every 5 seconds:
2024-01-15 10:30:05 - Starting scheduled rate ingestion
2024-01-15 10:30:06 - Successfully ingested 8 rates in 1234ms
2024-01-15 10:30:10 - Starting scheduled rate ingestion
2024-01-15 10:30:11 - Successfully ingested 8 rates in 1189ms
```

---

## 📊 Performance Characteristics

### Ingestion Timing

| Stage | Duration | Notes |
|-------|----------|-------|
| **External API Call** | 500-2000ms | Depends on provider latency |
| **Validation** | 10-50ms | In-memory processing |
| **Event Hub Publish** | 100-300ms | Azure Event Hubs latency |
| **Total** | 610-2350ms | Per ingestion cycle |

### Throughput

```
Ingestion Frequency: Every 5 seconds
Currency Pairs: 8
Events per cycle: 8
Events per minute: 96
Events per hour: 5,760
Events per day: 138,240
```

### Resource Usage

```yaml
# K8s deployment.yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

**Why these numbers?**
- Low CPU: Scheduled task, not request-driven
- Low memory: Processes small batches (8 rates)
- Scales vertically (doesn't need multiple replicas)

---

## 🔒 Circuit Breaker States

### State Machine

```
    [CLOSED] ← Normal operation
        ↓
    50% failures in 10 calls
        ↓
    [OPEN] ← All calls fail fast
        ↓
    Wait 30 seconds
        ↓
    [HALF_OPEN] ← Allow 3 test calls
        ↓
    ┌──────────┴──────────┐
    ✅ Success            ❌ Failure
    ↓                     ↓
  [CLOSED]             [OPEN]
```

### Example Scenario: Provider Outage

**Timeline:**

```
10:00:00 - Call 1: SUCCESS ✅
10:00:05 - Call 2: SUCCESS ✅
10:00:10 - Call 3: FAILURE ❌ (Provider timeout)
10:00:15 - Call 4: FAILURE ❌
10:00:20 - Call 5: FAILURE ❌
10:00:25 - Call 6: FAILURE ❌
10:00:30 - Call 7: FAILURE ❌ (5/7 = 71% > 50% threshold)
           → Circuit OPENS 🔴

10:00:35 - Call 8: FAILED FAST (circuit open, didn't call provider)
10:00:40 - Call 9: FAILED FAST
...
10:01:00 - Wait 30s complete
           → Circuit moves to HALF_OPEN 🟡

10:01:05 - Call 10: SUCCESS ✅ (test call 1/3)
10:01:10 - Call 11: SUCCESS ✅ (test call 2/3)
10:01:15 - Call 12: SUCCESS ✅ (test call 3/3)
           → Circuit CLOSES 🟢
           → Normal operation resumed
```

---

## 🐛 Troubleshooting

### Issue: No rates being published

**Check:**
1. Scheduler enabled?
   ```bash
   curl http://localhost:8081/actuator/scheduledtasks
   ```

2. External provider reachable?
   ```bash
   curl https://api.exchangerate-api.com/v4/latest/EUR
   ```

3. Event Hub connection string configured?
   ```bash
   echo $EVENTHUB_CONNECTION_STRING
   ```

4. Check circuit breaker state:
   ```bash
   curl http://localhost:8081/actuator/health | jq '.components.circuitBreakers'
   ```

### Issue: Circuit breaker stuck in OPEN state

**Solution:**
```bash
# Check failure rate
curl http://localhost:8081/actuator/circuitbreakerevents

# Wait for wait-duration (30s by default)
# Or restart service
docker restart rate-ingestion-service
```

### Issue: High validation failures

**Check logs:**
```bash
docker logs rate-ingestion-service | grep "Invalid rate"
docker logs rate-ingestion-service | grep "Excessive deviation"
```

**Solution:**
```yaml
# Increase deviation threshold in application.yml
app:
  ingestion:
    validation:
      max-deviation-percent: 10.0  # Increase from 5.0
```

### Issue: Event Hub publish failures

**Error:**
```
Error publishing to Event Hub: com.azure.messaging.eventhubs.EventHubException
```

**Check:**
1. Connection string valid?
2. Event Hub exists?
3. Access policy has "Send" permission?
4. Event Hub not throttled? (check Azure portal)

---

## 🎯 Key Takeaways

### This Service Is:
✅ The **DATA SOURCE** for the entire system
✅ Scheduled (runs every 5 seconds)
✅ Write-only (publishes, doesn't read)
✅ Resilient (circuit breakers, retries)
✅ Stateless (can restart without data loss)

### This Service Is NOT:
❌ Request-driven (it's scheduled)
❌ Reading data (it's write-only)
❌ Serving clients (no REST API for rates)
❌ Consuming events (only publishes)

### Data Flow Summary:

```
External Provider (Reuters/Bloomberg)
        ↓
  ExternalFxProviderClient (fetch via API)
        ↓
  RateValidationService (validate)
        ↓
  CosmosDbWriter (write to database) ✅
        ↓
  EventHubPublisher (publish to event hub) ✅
        ↓
  Azure Event Hubs
        ├──→ websocket-service (consumes)
        └──→ [Future] Other consumers

  Cosmos DB
        ↓
  fx-rates-api (reads) ✅
```

### Critical Dependencies:

1. **External FX Provider API** - Source of truth
2. **Azure Cosmos DB** - Historical data storage ✅
3. **Azure Event Hubs** - Real-time distribution mechanism ✅
4. **Circuit Breaker** - Resilience during outages
5. **Scheduler** - Periodic ingestion

---

## 📚 Related Files

- `../fx-rates-api/` - Reads data from Cosmos DB
- `../websocket-service/` - Consumes Event Hub events
- `../common-lib/` - Shared models and events
- `../k8s/base/rate-ingestion-service-deployment.yaml` - K8s config
- `writer/CosmosDbWriter.java` - Cosmos DB integration
- `config/CosmosDbConfig.java` - Cosmos DB configuration

---

## ✅ Cosmos DB Integration - COMPLETED

### Implementation Overview:

**Files Created:**

1. **CosmosDbWriter.java** - Service for writing rates to Cosmos DB
   ```java
   @Service
   public class CosmosDbWriter {
       public int saveRates(List<FxRate> rates) {
           for (FxRate rate : rates) {
               container.createItem(rate);
           }
           return successCount;
       }
   }
   ```

2. **CosmosDbConfig.java** - Configuration for Cosmos DB client
   ```java
   @Bean
   public CosmosClient cosmosClient() {
       return new CosmosClientBuilder()
           .endpoint(endpoint)
           .key(key)
           .consistencyLevel(ConsistencyLevel.SESSION)
           .buildClient();
   }
   ```

**RateIngestionService Flow:**
```java
// 1. Validate rates
List<FxRate> validatedRates = validationService.validateRates(rates);

// 2. Write to Cosmos DB ✅
int savedCount = cosmosDbWriter.saveRates(validatedRates);

// 3. Publish to Event Hub ✅
eventHubPublisher.publishRateUpdates(validatedRates);
```

**Configuration Required:**
```yaml
azure:
  cosmos:
    endpoint: ${COSMOS_ENDPOINT}
    key: ${COSMOS_KEY}
    database: fxrates
    container: rates
```

**Environment Variables:**
```bash
COSMOS_ENDPOINT=https://your-cosmos.documents.azure.com:443/
COSMOS_KEY=your-cosmos-key
COSMOS_DATABASE=fxrates
```

Now **fx-rates-api** can read fresh data from Cosmos DB with guaranteed freshness of 0-10 seconds! 🎉

---

**Complete System Flow:**

```
rate-ingestion-service (every 5s)
    ├──→ Cosmos DB (writes) ✅
    │       ↓
    │   fx-rates-api (reads) ✅
    │       ↓
    │   REST API Clients
    │
    └──→ Event Hubs (publishes) ✅
            ↓
        websocket-service (consumes) ✅
            ↓
        WebSocket Clients
```
