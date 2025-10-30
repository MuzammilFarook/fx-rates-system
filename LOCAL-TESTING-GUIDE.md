# Local Testing Guide - Running with Azure Resources

## 🎯 Overview

This guide shows you how to **run all services locally** on your machine while connecting to **Azure cloud resources**.

**Setup:**
- ✅ Services run on your local machine (Windows)
- ✅ Connect to Azure Cosmos DB (cloud)
- ✅ Connect to Azure Event Hubs (cloud)
- ✅ Connect to local Redis (Docker)

**Why test locally first?**
- 🔍 Easier debugging
- ⚡ Faster iteration
- 💰 No deployment costs yet
- 📊 Verify Azure integration before deployment

---

## 📋 Prerequisites

### **1. Azure Resources Created** ✅

Complete: [AZURE-SETUP-GUIDE.md](AZURE-SETUP-GUIDE.md)

You should have:
- ✅ Azure Cosmos DB
- ✅ Azure Event Hubs
- ✅ `.env` file with connection strings

### **2. Local Tools Installed**

```bash
# Java 17
java -version

# Maven
mvn -version

# Docker (for Redis)
docker --version

# Git Bash or PowerShell
```

---

## 🚀 Step-by-Step Setup

### **Step 1: Start Local Redis**

**Option A: Docker (Recommended)**
```bash
# Start Redis container
docker run -d \
  --name fx-rates-redis \
  -p 6379:6379 \
  redis:7-alpine

# Verify Redis is running
docker ps | grep redis

# Test Redis connection
docker exec -it fx-rates-redis redis-cli ping
# Should return: PONG
```

**Option B: Windows Redis**
```powershell
# Download Redis for Windows
# https://github.com/microsoftarchive/redis/releases

# Or use Windows Subsystem for Linux (WSL)
wsl
sudo apt-get update
sudo apt-get install redis-server
redis-server
```

---

### **Step 2: Load Environment Variables**

**Windows PowerShell:**
```powershell
# Navigate to project root
cd C:\Bismillah\AIB\Fexco\fx-rates-system

# Load environment variables from .env file
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^#][^=]+)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
        Write-Host "✅ Set $name"
    }
}

# Verify variables are set
$env:COSMOS_ENDPOINT
$env:EVENTHUB_CONNECTION_STRING
```

**Git Bash:**
```bash
# Navigate to project root
cd /c/Bismillah/AIB/Fexco/fx-rates-system

# Load environment variables
export $(cat .env | grep -v '^#' | xargs)

# Verify
echo $COSMOS_ENDPOINT
echo $EVENTHUB_CONNECTION_STRING
```

---

### **Step 3: Build Common Library**

The common library must be installed first (used by all services):

```bash
cd common-lib

# Clean and install
mvn clean install -DskipTests

# Verify JAR created
ls target/*.jar
# Should show: fx-rates-common-lib-1.0.0-SNAPSHOT.jar

cd ..
```

---

### **Step 4: Test rate-ingestion-service**

**Terminal 1:**
```bash
cd rate-ingestion-service

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

**Expected Output:**
```
2024-01-15 10:30:00 - Starting Rate Ingestion Service
2024-01-15 10:30:02 - Cosmos DB client initialized
2024-01-15 10:30:02 - Event Hub producer initialized
2024-01-15 10:30:02 - Using provider: Alpha Vantage (or Demo/Mock Reuters)
2024-01-15 10:30:05 - Starting scheduled rate ingestion
2024-01-15 10:30:06 - Fetching rates from Alpha Vantage for 8 pairs
2024-01-15 10:30:07 - Validated 8/8 rates
2024-01-15 10:30:07 - Saved 8/8 rates to Cosmos DB ✅
2024-01-15 10:30:07 - Published 8 events to Event Hub ✅
2024-01-15 10:30:07 - Completed scheduled rate ingestion in 1234ms
```

**Verify Cosmos DB:**
```bash
# Check data in Cosmos DB (Azure Portal)
# Or use Azure CLI
az cosmosdb sql container query \
  --account-name YOUR_COSMOS_ACCOUNT \
  --resource-group fexco-fx-rates-rg \
  --database-name fxrates \
  --name rates \
  --query-text "SELECT * FROM c"
```

---

### **Step 5: Test fx-rates-api**

**Terminal 2:**
```bash
cd fx-rates-api

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

**Expected Output:**
```
2024-01-15 10:30:00 - Starting FX Rates API Service
2024-01-15 10:30:02 - Redis connected
2024-01-15 10:30:02 - Cosmos DB repository initialized
2024-01-15 10:30:02 - Tomcat started on port 8080
2024-01-15 10:30:02 - Swagger UI: http://localhost:8080/swagger-ui.html
```

**Test REST API:**
```bash
# Health check
curl http://localhost:8080/actuator/health

# Get single rate
curl http://localhost:8080/api/v1/rates/EUR/USD

# Get batch rates
curl -X POST http://localhost:8080/api/v1/rates/batch \
  -H "Content-Type: application/json" \
  -d '{
    "currencyPairs": ["EURUSD", "GBPUSD", "USDJPY"]
  }'
```

**Expected Response:**
```json
{
  "rate": {
    "currencyPair": "EURUSD",
    "rate": 1.0850,
    "bid": 1.0845,
    "ask": 1.0855,
    "timestamp": "2024-01-15T10:30:00Z",
    "source": "Alpha Vantage",
    "confidenceScore": 0.95
  },
  "fromCache": false,
  "latencyMs": 23,
  "message": "Success"
}
```

---

### **Step 6: Test websocket-service**

**Terminal 3:**
```bash
cd websocket-service

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

**Expected Output:**
```
2024-01-15 10:30:00 - Starting WebSocket Service
2024-01-15 10:30:02 - Redis connected
2024-01-15 10:30:02 - Starting Event Hub consumer
2024-01-15 10:30:03 - Event Hub consumer started successfully
2024-01-15 10:30:03 - WebSocket endpoint: ws://localhost:8082/ws/fx-rates
```

**Test WebSocket (JavaScript):**

Create `test-websocket.html`:
```html
<!DOCTYPE html>
<html>
<head>
    <title>FX Rates WebSocket Test</title>
</head>
<body>
    <h1>FX Rates WebSocket Test</h1>
    <div id="status">Connecting...</div>
    <div id="messages"></div>

    <script>
        const ws = new WebSocket('ws://localhost:8082/ws/fx-rates');
        const statusDiv = document.getElementById('status');
        const messagesDiv = document.getElementById('messages');

        ws.onopen = function() {
            statusDiv.innerText = 'Connected ✅';

            // Subscribe to currency pairs
            ws.send(JSON.stringify({
                action: 'subscribe',
                currencyPairs: ['EURUSD', 'GBPUSD', 'USDJPY']
            }));
        };

        ws.onmessage = function(event) {
            const data = JSON.parse(event.data);
            console.log('Received:', data);

            const messageDiv = document.createElement('div');
            messageDiv.innerHTML = `<strong>${new Date().toLocaleTimeString()}</strong>: ${JSON.stringify(data, null, 2)}`;
            messagesDiv.appendChild(messageDiv);
        };

        ws.onerror = function(error) {
            statusDiv.innerText = 'Error: ' + error;
        };

        ws.onclose = function() {
            statusDiv.innerText = 'Disconnected ❌';
        };
    </script>
</body>
</html>
```

**Open in browser:**
```bash
# Open test-websocket.html in browser
start test-websocket.html
```

**Expected Messages:**
```json
// Connection message
{
  "type": "connected",
  "sessionId": "abc-123-def",
  "message": "Connected to FX Rates WebSocket"
}

// Subscription confirmation
{
  "type": "subscribed",
  "currencyPairs": ["EURUSD", "GBPUSD", "USDJPY"],
  "message": "Successfully subscribed to 3 currency pairs"
}

// Rate updates (every 5 seconds)
{
  "type": "rateUpdate",
  "event": {
    "fxRate": {
      "currencyPair": "EURUSD",
      "rate": 1.0850,
      "bid": 1.0845,
      "ask": 1.0855
    }
  }
}
```

---

## 🔍 Complete Data Flow Test

### **Verify End-to-End Flow:**

```
1. rate-ingestion-service (Terminal 1)
   ├─→ Fetches from Alpha Vantage ✅
   ├─→ Writes to Cosmos DB ✅
   └─→ Publishes to Event Hubs ✅

2. websocket-service (Terminal 3)
   ├─→ Consumes from Event Hubs ✅
   └─→ Broadcasts to WebSocket clients ✅

3. fx-rates-api (Terminal 2)
   ├─→ Reads from Cosmos DB ✅
   ├─→ Caches in Redis ✅
   └─→ Serves REST API ✅
```

### **Timeline:**

```
10:30:00 - rate-ingestion fetches EURUSD = 1.0850
10:30:01 - Writes to Cosmos DB ✅
10:30:01 - Publishes to Event Hubs ✅

10:30:02 - websocket-service receives event ✅
10:30:02 - Broadcasts to connected clients ✅
10:30:02 - WebSocket client sees update ✅

10:30:03 - User calls REST API ✅
10:30:03 - fx-rates-api queries Cosmos DB ✅
10:30:03 - Caches in Redis (5s TTL) ✅
10:30:03 - Returns rate ✅

10:30:04 - Another user calls REST API ✅
10:30:04 - Cache HIT! ✅
10:30:04 - Returns in 2ms ⚡
```

---

## 📊 Monitoring & Verification

### **1. Check Redis Cache**

```bash
# Connect to Redis CLI
docker exec -it fx-rates-redis redis-cli

# List all keys
KEYS *

# Get a specific rate
GET fx-rate:EURUSD

# Check TTL
TTL fx-rate:EURUSD

# Exit
exit
```

### **2. Check Cosmos DB**

**Azure Portal:**
1. Go to Azure Portal
2. Navigate to your Cosmos DB account
3. Open "Data Explorer"
4. Browse `fxrates` → `rates`
5. See your data!

**Azure CLI:**
```bash
# Query rates
az cosmosdb sql container query \
  --account-name YOUR_COSMOS_ACCOUNT \
  --resource-group fexco-fx-rates-rg \
  --database-name fxrates \
  --name rates \
  --query-text "SELECT TOP 10 * FROM c ORDER BY c.timestamp DESC"
```

### **3. Check Event Hubs**

**Azure Portal:**
1. Go to Azure Portal
2. Navigate to Event Hubs namespace
3. Click on `fx-rates-updates`
4. View "Metrics" → Incoming/Outgoing messages

**Expected Metrics:**
- Incoming messages: ~96/minute (8 pairs × 12 times/minute)
- Outgoing messages: Same as incoming
- Consumer lag: 0 (real-time)

### **4. Check Application Insights (Optional)**

**Azure Portal:**
1. Go to Application Insights
2. View "Live Metrics"
3. See requests, failures, performance

---

## 🧪 Testing Scenarios

### **Scenario 1: Cache Hit**

```bash
# First request (cache miss)
time curl http://localhost:8080/api/v1/rates/EUR/USD
# Response time: ~50ms (Cosmos DB query)
# fromCache: false

# Second request (cache hit)
time curl http://localhost:8080/api/v1/rates/EUR/USD
# Response time: ~5ms (Redis cache)
# fromCache: true
```

### **Scenario 2: Cache Expiry**

```bash
# Request 1
curl http://localhost:8080/api/v1/rates/EUR/USD
# fromCache: false (first request)

# Wait 6 seconds (cache TTL = 5s)
sleep 6

# Request 2
curl http://localhost:8080/api/v1/rates/EUR/USD
# fromCache: false (cache expired, fresh data from Cosmos)
```

### **Scenario 3: Real-Time Updates**

```bash
# 1. Open WebSocket test page
start test-websocket.html

# 2. Watch rate-ingestion-service logs (Terminal 1)
#    Every 5 seconds you'll see:
#    "Starting scheduled rate ingestion"

# 3. Watch WebSocket page
#    Every 5 seconds you'll see:
#    New rate updates appear!

# 4. Verify latency
#    rate-ingestion log timestamp → WebSocket receive timestamp
#    Should be < 1 second! ⚡
```

### **Scenario 4: Multiple Clients**

```bash
# Open multiple browser tabs with test-websocket.html
# All tabs should receive the same updates simultaneously
```

---

## 🐛 Troubleshooting

### **Issue: rate-ingestion-service fails to start**

**Error:** "Cosmos DB endpoint not configured"
```bash
# Check environment variables
echo $COSMOS_ENDPOINT
echo $COSMOS_KEY

# If empty, reload .env file
export $(cat .env | grep -v '^#' | xargs)
```

**Error:** "Event Hub connection string invalid"
```bash
# Verify connection string format
echo $EVENTHUB_CONNECTION_STRING
# Should start with: Endpoint=sb://...

# Get new connection string from Azure
az eventhubs namespace authorization-rule keys list \
  --namespace-name YOUR_NAMESPACE \
  --resource-group fexco-fx-rates-rg \
  --name RootManageSharedAccessKey \
  --query "primaryConnectionString"
```

---

### **Issue: fx-rates-api returns 404**

**Error:** "FxRateNotFoundException"
```bash
# Check if rate-ingestion has written data
az cosmosdb sql container query \
  --account-name YOUR_COSMOS_ACCOUNT \
  --resource-group fexco-fx-rates-rg \
  --database-name fxrates \
  --name rates \
  --query-text "SELECT COUNT(1) as count FROM c"

# If count = 0, wait for rate-ingestion to run (every 5s)
```

---

### **Issue: Redis connection failed**

**Error:** "Unable to connect to Redis"
```bash
# Check if Redis is running
docker ps | grep redis

# If not running, start it
docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine

# Test connection
docker exec -it fx-rates-redis redis-cli ping
```

---

### **Issue: WebSocket connection refused**

**Error:** "Connection refused"
```bash
# Check if websocket-service is running
curl http://localhost:8082/actuator/health

# Check logs for errors
# Look for: "WebSocket endpoint: ws://localhost:8082/ws/fx-rates"
```

---

### **Issue: No updates in WebSocket**

**Possible causes:**
1. rate-ingestion not running → Start Terminal 1
2. Event Hub consumer not connected → Check logs
3. Not subscribed to pairs → Send subscribe message

**Debug:**
```bash
# Check rate-ingestion logs
# Should see every 5s:
# "Successfully ingested 8 rates"

# Check websocket-service logs
# Should see:
# "Received event: EURUSD"
# "Broadcasting EURUSD update to X subscribers"
```

---

## 📈 Performance Verification

### **Expected Latencies:**

| Operation | Expected | Actual |
|-----------|----------|--------|
| **Cosmos DB write** | < 100ms | ? |
| **Cosmos DB read (first)** | < 50ms | ? |
| **Redis cache hit** | < 5ms | ⚡ |
| **Event Hub publish** | < 100ms | ? |
| **Event Hub → WebSocket** | < 1s | ? |
| **End-to-end (provider → WebSocket)** | < 2s | ? |

### **Test Latencies:**

```bash
# Cosmos DB query latency
time curl http://localhost:8080/api/v1/rates/EUR/USD

# Cache hit latency
time curl http://localhost:8080/api/v1/rates/EUR/USD

# Event Hub → WebSocket latency
# Compare timestamps:
# rate-ingestion log: "10:30:05.123"
# WebSocket receive:  "10:30:05.892"
# Latency: 769ms ✅
```

---

## ✅ Success Criteria

You've successfully set up local testing if:

- ✅ **rate-ingestion-service** fetches rates every 5s
- ✅ **Cosmos DB** contains FX rate data
- ✅ **Event Hubs** receives events (check metrics)
- ✅ **fx-rates-api** serves rates via REST
- ✅ **Redis cache** improves response time (< 5ms)
- ✅ **websocket-service** pushes real-time updates
- ✅ **WebSocket clients** receive updates every 5s
- ✅ **End-to-end latency** < 2 seconds

---

## 🎉 Next Steps

After successful local testing:

1. ✅ **Optimize configurations** (cache TTL, RU consumption, etc.)
2. ✅ **Add monitoring** (Application Insights dashboards)
3. ✅ **Prepare deployment** (Docker images, Kubernetes)
4. ✅ **Load testing** (simulate multiple clients)

Continue to: `DEPLOYMENT-GUIDE.md` (when ready to deploy to Azure)

---

## 💡 Tips

1. **Keep all 3 terminals open** to monitor logs
2. **Use tmux/screen** for terminal multiplexing
3. **Monitor Azure costs** in Azure Portal
4. **Use Application Insights** to track performance
5. **Test with Alpha Vantage** first, then Mock Reuters
6. **Stop services** when not testing (save Azure costs)

---

**Congratulations!** 🎉 You're now running a complete FX rates system locally with Azure integration!
