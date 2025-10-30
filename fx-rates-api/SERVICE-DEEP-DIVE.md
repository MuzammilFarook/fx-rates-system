# FX Rates API Service - Deep Dive

## 🎯 Service Purpose

**fx-rates-api** is a **READ-ONLY** REST API service that provides FX rate data to partners and clients.

### What It Does ✅
- Serves REST API endpoints for querying FX rates
- Caches results in Redis for performance
- Falls back to Cosmos DB when cache misses
- Provides Swagger/OpenAPI documentation
- Implements circuit breakers for resilience
- Monitors performance with Application Insights

### What It Does NOT Do ❌
- ❌ Does NOT fetch rates from external providers (that's rate-ingestion-service)
- ❌ Does NOT publish to Event Hubs (that's rate-ingestion-service)
- ❌ Does NOT consume from Event Hubs (that's websocket-service)
- ❌ Does NOT push real-time updates (that's websocket-service)

---

## 📊 Data Flow

### Read Path (Normal Request)

```
1. Client Request
   ↓
2. FxRatesController (REST endpoint)
   ↓
3. FxRateServiceImpl
   ↓
4. Check Redis Cache
   ├─ Cache HIT → Return cached data (sub-ms latency) ✅
   └─ Cache MISS ↓
      ↓
5. Query Cosmos DB (via Circuit Breaker)
   ↓
6. Store in Redis Cache (5 second TTL)
   ↓
7. Return to Client
```

### Circuit Breaker Fallback (Cosmos DB Down)

```
Client Request
   ↓
FxRateServiceImpl
   ↓
Check Redis Cache
   ├─ Cache HIT → Return (even if stale) ✅
   └─ Cache MISS ↓
      ↓
Query Cosmos DB
   ↓
Circuit Breaker OPEN (Cosmos DB unreachable)
   ↓
Return stale data from cache (if available)
   OR
Throw FxRateNotFoundException
```

---

## 🏗️ Architecture Components

### 1. Controller Layer

**File:** `controller/FxRatesController.java`

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/rates/{from}/{to}` | Get single FX rate |
| POST | `/api/v1/rates/batch` | Get multiple rates |
| GET | `/api/v1/rates/history/{from}/{to}` | Get historical rates |
| GET | `/api/v1/rates/pairs` | Get supported currency pairs |
| GET | `/api/v1/rates/health` | Health check |

**Example Request:**
```bash
curl http://localhost:8080/api/v1/rates/EUR/USD
```

**Example Response:**
```json
{
  "rate": {
    "id": "EURUSD_...",
    "fromCurrency": "EUR",
    "toCurrency": "USD",
    "currencyPair": "EURUSD",
    "rate": 1.0850,
    "bid": 1.0845,
    "ask": 1.0855,
    "timestamp": "2024-01-15T10:30:00.000Z",
    "source": "ExternalFXProvider",
    "confidenceScore": 0.95
  },
  "message": "Success",
  "retrievedAt": "2024-01-15T10:30:05.123Z",
  "fromCache": true,
  "latencyMs": 2
}
```

### 2. Service Layer

**File:** `service/FxRateServiceImpl.java`

**Key Methods:**

1. **`getFxRate(from, to)`**
   - Checks Redis cache first
   - Falls back to Cosmos DB on cache miss
   - Implements circuit breaker pattern
   - Provides fallback to stale cache

2. **`getBatchFxRates(request)`**
   - Queries multiple rates efficiently
   - Checks cache for each pair
   - Batches database queries

3. **`getHistoricalRates(...)`**
   - Queries time-series data from Cosmos DB
   - Used for analytics and reporting

**Annotations Used:**
- `@Cacheable` - Spring cache abstraction
- `@CircuitBreaker` - Resilience4j circuit breaker
- `@Retry` - Automatic retries on transient failures

**Circuit Breaker Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      cosmosdb:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

### 3. Repository Layer

**File:** `repository/CosmosDbFxRateRepository.java`

**Key Methods:**

1. **`findLatestByCurrencyPair(currencyPair)`**
   ```java
   // SQL query to Cosmos DB
   SELECT TOP 1 * FROM c
   WHERE c.currencyPair = 'EURUSD'
   ORDER BY c.timestamp DESC
   ```

2. **`findHistoricalRates(currencyPair, startDate, endDate, limit)`**
   ```java
   SELECT TOP 100 * FROM c
   WHERE c.currencyPair = 'EURUSD'
   AND c.timestamp >= '2024-01-01'
   AND c.timestamp <= '2024-01-31'
   ORDER BY c.timestamp DESC
   ```

3. **`save(fxRate)`**
   - Saves rate to Cosmos DB
   - Generates unique ID
   - Sets timestamps

**Note:** This service primarily READS. The WRITE operations happen via rate-ingestion-service.

### 4. Configuration Layer

**Files:**
- `config/RedisConfig.java` - Redis template setup
- `config/CosmosDbConfig.java` - Cosmos DB client
- `config/OpenApiConfig.java` - Swagger UI

**RedisConfig:**
```java
@Bean
public RedisTemplate<String, FxRate> redisTemplate() {
    // Configures serialization
    // Jackson for FxRate objects
    // String for keys
}
```

**CosmosDbConfig:**
```java
@Bean
public CosmosClient cosmosClient() {
    return new CosmosClientBuilder()
        .endpoint(endpoint)
        .key(key)
        .directMode()  // Better performance
        .consistencyLevel(SESSION)  // Balanced consistency
        .buildClient();
}
```

---

## 🔧 Dependencies Explained

### Required Dependencies ✅

1. **spring-boot-starter-web** - REST endpoints
2. **spring-boot-starter-data-redis** - Redis caching
3. **spring-boot-starter-actuator** - Health checks, metrics
4. **azure-cosmos** - Cosmos DB access
5. **resilience4j-spring-boot3** - Circuit breakers
6. **springdoc-openapi** - Swagger UI
7. **lettuce-core** - Redis client
8. **micrometer-registry-prometheus** - Metrics export


---

## 📝 Configuration Files

### application.yml

```yaml
server:
  port: 8080
  servlet:
    context-path: /api/v1

spring:
  application:
    name: fx-rates-api

  # Redis Configuration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 2000ms

# Azure Cosmos DB
azure:
  cosmos:
    endpoint: ${COSMOS_ENDPOINT:}
    key: ${COSMOS_KEY:}
    database: fxrates
    container: rates

# Application Insights (Monitoring)
  application-insights:
    instrumentation-key: ${APPINSIGHTS_INSTRUMENTATIONKEY:}
    enabled: ${APPINSIGHTS_ENABLED:false}

# Circuit Breaker Configuration
resilience4j:
  circuitbreaker:
    instances:
      cosmosdb:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

### Environment Variables Needed

```bash
# Required
REDIS_HOST=localhost
REDIS_PORT=6379
COSMOS_ENDPOINT=https://your-cosmos.documents.azure.com:443/
COSMOS_KEY=your-cosmos-key
COSMOS_DATABASE=fxrates

# Optional (for monitoring)
APPINSIGHTS_INSTRUMENTATIONKEY=your-key
```

---

## 🚀 Running the Service

### Option 1: Maven

```bash
cd fx-rates-api

# Build
mvn clean package

# Run
mvn spring-boot:run

# Or run JAR directly
java -jar target/fx-rates-api-1.0.0-SNAPSHOT.jar
```

### Option 2: Docker

```bash
# Build image
docker build -t fx-rates-api:latest -f fx-rates-api/Dockerfile .

# Run container
docker run -p 8080:8080 \
  -e REDIS_HOST=redis \
  -e COSMOS_ENDPOINT=https://... \
  -e COSMOS_KEY=your-key \
  fx-rates-api:latest
```

### Option 3: Docker Compose

```bash
docker-compose up fx-rates-api
```

---

## 🧪 Testing the API

### 1. Health Check

```bash
curl http://localhost:8080/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

### 2. Get Single Rate

```bash
curl http://localhost:8080/api/v1/rates/EUR/USD
```

### 3. Batch Request

```bash
curl -X POST http://localhost:8080/api/v1/rates/batch \
  -H "Content-Type: application/json" \
  -d '{
    "currencyPairs": ["EURUSD", "GBPUSD", "USDJPY"]
  }'
```

### 4. Historical Rates

```bash
curl "http://localhost:8080/api/v1/rates/history/EUR/USD?limit=10"
```

### 5. Swagger UI

Open: http://localhost:8080/api/v1/swagger-ui.html

---

## 📊 Performance Characteristics

### Cache Performance

| Scenario | Latency | Source |
|----------|---------|--------|
| Cache HIT | < 5ms | Redis |
| Cache MISS | 20-50ms | Cosmos DB |
| Circuit Breaker (fallback) | < 5ms | Stale Redis cache |

### Cache TTL Strategy

```java
// Cache configuration
TTL: 5 seconds
Hit ratio target: > 95%

// Why 5 seconds?
- Balance between freshness and performance
- Rate ingestion happens every 5 seconds
- Reduces Cosmos DB load by 95%+
```

### Scaling

**Horizontal Pod Autoscaler (HPA):**
```yaml
minReplicas: 3
maxReplicas: 20
targetCPUUtilization: 70%
targetMemoryUtilization: 80%
```

**Why these numbers?**
- Min 3: High availability (survives 1-2 pod failures)
- Max 20: Handles 10-100x traffic spikes
- CPU 70%: Room for burst traffic
- Memory 80%: Prevents OOM

---

## 🔒 Security

### Authentication

**Current:** None (demo)
**Production:** OAuth 2.0 via Azure API Management

```
Client → API Management (OAuth check)
            ↓
       fx-rates-api
```

### Network Security

**Kubernetes:**
- Private ClusterIP service
- Exposed via API Management only
- No direct external access

---

## 📈 Monitoring & Observability

### Metrics Exposed

**Endpoint:** `/actuator/prometheus`

**Key Metrics:**
- `http_server_requests` - Request latency & count
- `spring_data_redis_cache_gets` - Cache hit/miss ratio
- `resilience4j_circuitbreaker_state` - Circuit breaker status

### Application Insights

**Tracked:**
- Request traces
- Exception tracking
- Dependency calls (Redis, Cosmos DB)
- Custom metrics

---

## 🐛 Troubleshooting

### Issue: High latency

**Check:**
1. Redis cache hit ratio: `GET /actuator/metrics/cache.gets`
2. Cosmos DB throttling: Check Application Insights
3. Circuit breaker state: `GET /actuator/circuitbreakers`

### Issue: 404 Not Found

**Solution:**
- Ensure context path: `/api/v1/rates/{from}/{to}`
- Not: `/rates/{from}/{to}`

### Issue: Redis connection failed

**Solution:**
```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Verify connection
redis-cli ping
```

---

## 🎯 Key Takeaways

### This Service Is:
✅ Stateless (scales horizontally)
✅ Read-heavy (caching is critical)
✅ Resilient (circuit breakers, fallbacks)
✅ Observable (metrics, traces, logs)

### This Service Is NOT:
❌ Writing data (that's rate-ingestion)
❌ Real-time push (that's websocket-service)
❌ Fetching from providers (that's rate-ingestion)

### Data Flow Summary:
```
rate-ingestion → Cosmos DB ← fx-rates-api → Client
                     ↓           ↑
                 Event Hubs   Redis Cache
                     ↓
              websocket-service → WebSocket Clients
```

---

## 📚 Related Files

- `../rate-ingestion-service/` - Writes data
- `../websocket-service/` - Real-time push
- `../common-lib/` - Shared models
- `../k8s/base/fx-rates-api-deployment.yaml` - K8s config

---

