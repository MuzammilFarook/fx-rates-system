# WebSocket Service - Deep Dive

## 🎯 Service Purpose

**websocket-service** is the **REAL-TIME DISTRIBUTION** service that pushes FX rate updates to connected clients via WebSocket.

### What It Does ✅
- Provides WebSocket endpoint for client connections
- Manages client subscriptions to specific currency pairs
- **Consumes from Event Hubs** (receives updates from rate-ingestion-service)
- Broadcasts updates to subscribed WebSocket clients in real-time
- Handles connection lifecycle (connect, subscribe, unsubscribe, disconnect)
- Implements heartbeat/ping-pong for connection health
- Tracks subscription statistics

### What It Does NOT Do ❌
- ❌ Does NOT fetch from external providers (that's rate-ingestion-service)
- ❌ Does NOT publish to Event Hubs (that's rate-ingestion-service)
- ❌ Does NOT serve REST API for rates (that's fx-rates-api)
- ❌ Does NOT write to Cosmos DB (that's rate-ingestion-service)

---

## 📊 Data Flow

### Real-Time Update Flow

```
1. Event Hub (receives from rate-ingestion-service)
   ↓
2. EventHubConsumer (@PostConstruct listener)
   ├─→ Deserialize FxRateUpdatedEvent
   ├─→ Extract currency pair
   └─→ Forward to SubscriptionService
   ↓
3. SubscriptionService.broadcastRateUpdate()
   ├─→ Find all sessions subscribed to this pair
   ├─→ Serialize to JSON
   └─→ Send to each WebSocket session
   ↓
4. WebSocket Clients receive update
```

### Client Connection Flow

```
1. Client connects to ws://server:8082/ws/fx-rates
   ↓
2. FxRatesWebSocketHandler.afterConnectionEstablished()
   ├─→ Register session in SubscriptionService
   └─→ Send welcome message
   ↓
3. Client sends subscribe message:
   {
     "action": "subscribe",
     "currencyPairs": ["EURUSD", "GBPUSD"]
   }
   ↓
4. FxRatesWebSocketHandler.handleSubscribe()
   ├─→ Validate request
   ├─→ Add to subscription maps
   └─→ Send confirmation
   ↓
5. Client receives real-time updates:
   {
     "type": "rateUpdate",
     "event": {
       "currencyPair": "EURUSD",
       "rate": 1.0850,
       ...
     }
   }
```

### Subscription Management

```
SubscriptionService maintains 3 maps:

1. sessions: Map<sessionId, WebSocketSession>
   └─→ Tracks all active WebSocket sessions

2. subscriptions: Map<currencyPair, Set<sessionId>>
   └─→ For each pair, which sessions are subscribed
   Example: "EURUSD" → ["session-1", "session-2", "session-3"]

3. sessionSubscriptions: Map<sessionId, Set<currencyPair>>
   └─→ For each session, which pairs they're subscribed to
   Example: "session-1" → ["EURUSD", "GBPUSD", "USDJPY"]
```

---

## 🏗️ Architecture Components

### 1. WebSocket Configuration

**File:** `config/WebSocketConfig.java`

**Endpoint Registration:**

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(fxRatesWebSocketHandler, "/ws/fx-rates")
                .setAllowedOrigins("*");  // ⚠️ Configure for production!
    }
}
```

**WebSocket URL:**
```
ws://localhost:8082/ws/fx-rates
```

**Production Note:**
```java
// For production, restrict origins:
.setAllowedOrigins("https://your-domain.com", "https://partner-domain.com")
```

---

### 2. WebSocket Handler

**File:** `handler/FxRatesWebSocketHandler.java`

**Key Methods:**

#### Connection Established

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) {
    log.info("WebSocket connection established: {}", session.getId());
    subscriptionService.registerSession(session);

    // Send welcome message
    {
        "type": "connected",
        "message": "Connected to FX Rates WebSocket",
        "sessionId": "abc-123-def"
    }
}
```

#### Handle Messages

```java
@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    Map<String, Object> payload = objectMapper.readValue(message.getPayload());
    String action = payload.get("action");

    switch (action) {
        case "subscribe":   handleSubscribe(session, payload);
        case "unsubscribe": handleUnsubscribe(session, payload);
        case "ping":        handlePing(session);
    }
}
```

#### Connection Closed

```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    log.info("Connection closed: {} with status: {}", session.getId(), status);
    subscriptionService.unregisterSession(session);
    // Cleanup: removes session and all its subscriptions
}
```

---

### 3. Subscription Service

**File:** `service/SubscriptionService.java`

**Core Data Structures:**

```java
@Service
public class SubscriptionService {

    // Map of sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Map of currencyPair -> Set of sessionIds
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();

    // Map of sessionId -> Set of currencyPairs
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
}
```

**Why 3 Maps?**

1. **sessions** - Quick session lookup for sending messages
2. **subscriptions** - Quick lookup: "Who's subscribed to EURUSD?"
3. **sessionSubscriptions** - Quick cleanup when session disconnects

**Example State:**

```java
// 3 clients connected, 2 subscribed to EURUSD

sessions = {
    "session-1" → WebSocketSession@abc,
    "session-2" → WebSocketSession@def,
    "session-3" → WebSocketSession@ghi
}

subscriptions = {
    "EURUSD" → ["session-1", "session-2"],
    "GBPUSD" → ["session-1"],
    "USDJPY" → ["session-3"]
}

sessionSubscriptions = {
    "session-1" → ["EURUSD", "GBPUSD"],
    "session-2" → ["EURUSD"],
    "session-3" → ["USDJPY"]
}
```

**Subscribe Method:**

```java
public void subscribe(WebSocketSession session, List<String> currencyPairs) {
    String sessionId = session.getId();

    for (String pair : currencyPairs) {
        // Add to subscriptions map (pair → sessions)
        subscriptions.computeIfAbsent(pair, k -> new CopyOnWriteArraySet<>())
                     .add(sessionId);

        // Add to session subscriptions (session → pairs)
        sessionSubscriptions.get(sessionId).add(pair);
    }
}
```

**Broadcast Method:**

```java
public void broadcastRateUpdate(FxRateUpdatedEvent event) {
    String currencyPair = event.getFxRate().getCurrencyPair();
    Set<String> subscribers = subscriptions.get(currencyPair);

    if (subscribers == null || subscribers.isEmpty()) {
        return;  // No one subscribed
    }

    // Serialize event
    String message = objectMapper.writeValueAsString(Map.of(
        "type", "rateUpdate",
        "event", event
    ));

    // Send to all subscribers
    for (String sessionId : subscribers) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }
}
```

**Cleanup on Disconnect:**

```java
public void unregisterSession(WebSocketSession session) {
    String sessionId = session.getId();

    // 1. Get all pairs this session subscribed to
    Set<String> pairs = sessionSubscriptions.remove(sessionId);

    // 2. Remove session from each pair's subscriber list
    if (pairs != null) {
        pairs.forEach(pair -> {
            Set<String> subscribers = subscriptions.get(pair);
            if (subscribers != null) {
                subscribers.remove(sessionId);
                if (subscribers.isEmpty()) {
                    subscriptions.remove(pair);  // No more subscribers
                }
            }
        });
    }

    // 3. Remove session itself
    sessions.remove(sessionId);
}
```

---

### 4. Event Hub Consumer

**File:** `consumer/EventHubConsumer.java`

**Initialization:**

```java
@Component
public class EventHubConsumer {

    private EventProcessorClient eventProcessorClient;

    @PostConstruct
    public void start() {
        log.info("Starting Event Hub consumer for: {}", eventHubName);

        eventProcessorClient = new EventProcessorClientBuilder()
            .consumerGroup(consumerGroup)  // "websocket-service"
            .connectionString(connectionString, eventHubName)
            .processEvent(this::processEvent)
            .processError(this::processError)
            .buildEventProcessorClient();

        eventProcessorClient.start();
    }
}
```

**Process Event:**

```java
private void processEvent(EventContext eventContext) {
    // 1. Get event data
    String eventData = eventContext.getEventData().getBodyAsString();

    // 2. Deserialize
    FxRateUpdatedEvent event = objectMapper.readValue(
        eventData,
        FxRateUpdatedEvent.class
    );

    // 3. Broadcast to WebSocket clients
    subscriptionService.broadcastRateUpdate(event);

    // 4. Checkpoint (mark as processed)
    eventContext.updateCheckpointAsync();
}
```

**Consumer Group Concept:**

```
Event Hub: fx-rates-updates
├─→ Consumer Group: "websocket-service" (this service)
├─→ Consumer Group: "analytics-service" (future)
└─→ Consumer Group: "audit-service" (future)

Each consumer group gets ALL events independently!
```

**Checkpointing:**

```java
// Mark event as processed
eventContext.updateCheckpointAsync();

// If service restarts, it resumes from last checkpoint
// Won't re-process already handled events
```

---

## 🔧 Client Protocol

### Message Types

#### 1. Connect (Server → Client)

**When:** Client first connects

```json
{
  "type": "connected",
  "message": "Connected to FX Rates WebSocket",
  "sessionId": "abc-123-def"
}
```

#### 2. Subscribe (Client → Server)

**When:** Client wants to receive updates for specific pairs

```json
{
  "action": "subscribe",
  "currencyPairs": ["EURUSD", "GBPUSD", "USDJPY"]
}
```

**Response (Server → Client):**

```json
{
  "type": "subscribed",
  "currencyPairs": ["EURUSD", "GBPUSD", "USDJPY"],
  "message": "Successfully subscribed to 3 currency pairs"
}
```

#### 3. Rate Update (Server → Client)

**When:** New rate published from rate-ingestion-service

```json
{
  "type": "rateUpdate",
  "event": {
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "eventType": "FxRateUpdated",
    "timestamp": "2024-01-15T10:30:00.000Z",
    "fxRate": {
      "currencyPair": "EURUSD",
      "rate": 1.0850,
      "bid": 1.0845,
      "ask": 1.0855,
      "timestamp": "2024-01-15T10:30:00.000Z",
      "source": "ExternalFXProvider",
      "confidenceScore": 0.95
    }
  }
}
```

#### 4. Unsubscribe (Client → Server)

```json
{
  "action": "unsubscribe",
  "currencyPairs": ["GBPUSD"]
}
```

**Response:**

```json
{
  "type": "unsubscribed",
  "currencyPairs": ["GBPUSD"]
}
```

#### 5. Ping/Pong (Heartbeat)

**Client → Server:**

```json
{
  "action": "ping"
}
```

**Server → Client:**

```json
{
  "type": "pong",
  "timestamp": "1705315800000"
}
```

#### 6. Error (Server → Client)

```json
{
  "type": "error",
  "message": "Invalid currencyPairs format"
}
```

---

## 📝 Configuration

### application.yml Breakdown

**Server Configuration:**
```yaml
server:
  port: 8082  # Different from fx-rates-api (8080), rate-ingestion (8081)
```

**Event Hub Configuration:**
```yaml
azure:
  eventhub:
    namespace: ${EVENTHUB_NAMESPACE:}
    connection-string: ${EVENTHUB_CONNECTION_STRING:}
    topic: fx-rates-updates
    consumer-group: websocket-service  # ← Unique consumer group
```

**Connection Limits:**
```yaml
app:
  websocket:
    max-connections: 10000
    max-subscriptions-per-connection: 50
    heartbeat:
      enabled: true
      interval-seconds: 30
```

### Environment Variables Needed

```bash
# Required
EVENTHUB_CONNECTION_STRING=Endpoint=sb://...;SharedAccessKeyName=...;SharedAccessKey=...
EVENTHUB_NAMESPACE=fexco-eventhub
EVENTHUB_TOPIC=fx-rates-updates
EVENTHUB_CONSUMER_GROUP=websocket-service

# Optional (for Redis pub/sub - future)
REDIS_HOST=localhost
REDIS_PORT=6379

# Optional (monitoring)
APPINSIGHTS_INSTRUMENTATIONKEY=your-key
```

---

## 🚀 Running the Service

### Option 1: Maven

```bash
cd websocket-service

# Build
mvn clean package

# Run
mvn spring-boot:run

# Or run JAR
java -jar target/websocket-service-1.0.0-SNAPSHOT.jar
```

### Option 2: Docker

```bash
# Build image
docker build -t websocket-service:latest -f websocket-service/Dockerfile .

# Run container
docker run -p 8082:8082 \
  -e EVENTHUB_CONNECTION_STRING=... \
  -e EVENTHUB_NAMESPACE=fexco-eventhub \
  websocket-service:latest
```

### Option 3: Docker Compose

```bash
docker-compose up websocket-service
```

**What you should see in logs:**

```
2024-01-15 10:30:00 - Starting WebSocket Service
2024-01-15 10:30:01 - Starting Event Hub consumer for: fx-rates-updates
2024-01-15 10:30:02 - Event Hub consumer started successfully
2024-01-15 10:30:05 - WebSocket connection established: session-abc-123
2024-01-15 10:30:05 - Registered session: session-abc-123. Total sessions: 1
2024-01-15 10:30:06 - Session session-abc-123 subscribed to: [EURUSD, GBPUSD]
2024-01-15 10:30:10 - Received event: FxRateUpdated for EURUSD
2024-01-15 10:30:10 - Broadcasting EURUSD update to 1 subscribers
```

---

## 🧪 Testing the Service

### 1. Health Check

```bash
curl http://localhost:8082/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

### 2. WebSocket Connection (JavaScript)

```javascript
// Connect
const ws = new WebSocket('ws://localhost:8082/ws/fx-rates');

ws.onopen = function() {
    console.log('Connected!');

    // Subscribe to currency pairs
    ws.send(JSON.stringify({
        action: 'subscribe',
        currencyPairs: ['EURUSD', 'GBPUSD', 'USDJPY']
    }));
};

ws.onmessage = function(event) {
    const data = JSON.parse(event.data);
    console.log('Received:', data);

    if (data.type === 'rateUpdate') {
        console.log('Rate Update:', data.event.fxRate);
        // Update UI with new rate
    }
};

ws.onerror = function(error) {
    console.error('WebSocket error:', error);
};

ws.onclose = function() {
    console.log('Disconnected');
};

// Send ping every 30 seconds (heartbeat)
setInterval(() => {
    ws.send(JSON.stringify({ action: 'ping' }));
}, 30000);

// Unsubscribe from a pair
ws.send(JSON.stringify({
    action: 'unsubscribe',
    currencyPairs: ['GBPUSD']
}));
```

### 3. WebSocket Connection (Python)

```python
import websocket
import json

def on_message(ws, message):
    data = json.loads(message)
    print(f"Received: {data}")

    if data['type'] == 'rateUpdate':
        rate = data['event']['fxRate']
        print(f"{rate['currencyPair']}: {rate['rate']}")

def on_open(ws):
    print("Connected!")

    # Subscribe
    ws.send(json.dumps({
        'action': 'subscribe',
        'currencyPairs': ['EURUSD', 'GBPUSD']
    }))

ws = websocket.WebSocketApp(
    'ws://localhost:8082/ws/fx-rates',
    on_message=on_message,
    on_open=on_open
)

ws.run_forever()
```

### 4. Check Subscription Stats

```bash
# Get subscription statistics (if exposed via actuator)
curl http://localhost:8082/actuator/metrics
```

**Custom Metrics:**
- `websocket.sessions.active` - Current active sessions
- `websocket.subscriptions.total` - Total active subscriptions
- `websocket.messages.sent` - Total messages sent
- `websocket.events.received` - Events received from Event Hub

---

## 📊 Performance Characteristics

### Latency Breakdown

| Stage | Duration | Notes |
|-------|----------|-------|
| **Event Hub to Consumer** | 10-50ms | Azure Event Hubs latency |
| **Deserialization** | 1-5ms | JSON parsing |
| **Broadcast to WebSockets** | 1-10ms per client | Depends on client count |
| **Total** | 12-65ms | End-to-end latency |

### Throughput

**Per WebSocket connection:**
```
Rate updates: Every 5 seconds (from rate-ingestion)
Subscriptions: Up to 50 pairs per connection
Messages/minute per client: 12 × subscriptions
```

**Example:**
```
Client subscribes to 10 pairs
Rate ingestion every 5 seconds
Messages/minute = 12 × 10 = 120 messages/minute/client
```

**Scaling:**
```
Max connections: 10,000 (configurable)
Max subscriptions per connection: 50
Total potential subscriptions: 500,000
```

### Resource Usage

```yaml
# K8s deployment.yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

**Why these numbers?**
- Higher memory: Maintains all WebSocket connections in memory
- Moderate CPU: Broadcasting to many clients
- Scales horizontally: Can run multiple replicas

---

## 🔄 Scaling Strategy

### Horizontal Scaling (Multiple Replicas)

**Problem:** If you run 3 replicas, clients connect to different instances:

```
Client 1 → websocket-service-replica-1
Client 2 → websocket-service-replica-2
Client 3 → websocket-service-replica-3
```

Each replica has its own subscription map!

**Solution: Redis Pub/Sub** (for multi-replica setups)

```
Event Hub → websocket-service-replica-1
                    ↓
               Redis Pub/Sub
                    ↓
         ┌──────────┼──────────┐
         ↓          ↓          ↓
    replica-1   replica-2   replica-3
         ↓          ↓          ↓
    Client 1    Client 2    Client 3
```

**Implementation (Future):**

```java
@Service
public class RedisPubSubService {

    @PostConstruct
    public void subscribe() {
        redisTemplate.listenTo("fx-rate-updates", this::onMessage);
    }

    public void onMessage(String message) {
        FxRateUpdatedEvent event = deserialize(message);
        subscriptionService.broadcastRateUpdate(event);
    }
}
```

**Update EventHubConsumer:**

```java
private void processEvent(EventContext eventContext) {
    FxRateUpdatedEvent event = deserialize(eventContext);

    // Publish to Redis (instead of direct broadcast)
    redisPubSubService.publish("fx-rate-updates", event);

    eventContext.updateCheckpointAsync();
}
```

---

## 🐛 Troubleshooting

### Issue: Clients not receiving updates

**Check:**

1. Is Event Hub consumer running?
   ```bash
   docker logs websocket-service | grep "Event Hub consumer started"
   ```

2. Is client subscribed?
   ```bash
   docker logs websocket-service | grep "subscribed to"
   ```

3. Are events being received from Event Hub?
   ```bash
   docker logs websocket-service | grep "Received event"
   ```

4. Check subscription stats:
   ```bash
   # Via logs
   docker logs websocket-service | grep "Broadcasting"
   ```

### Issue: WebSocket connection drops

**Symptoms:**
```
WebSocket connection closed: session-123 with status: GOING_AWAY
```

**Causes:**
1. Client didn't send heartbeat (ping)
2. Network timeout
3. Load balancer timeout

**Solution:**
```javascript
// Client-side: Send ping every 30 seconds
setInterval(() => {
    ws.send(JSON.stringify({ action: 'ping' }));
}, 30000);
```

### Issue: High memory usage

**Symptoms:**
```
OutOfMemoryError: Java heap space
```

**Causes:**
- Too many WebSocket connections
- Too many subscriptions
- Memory leak in session management

**Solutions:**

1. **Limit connections:**
   ```yaml
   app:
     websocket:
       max-connections: 5000  # Reduce from 10000
   ```

2. **Increase memory:**
   ```yaml
   resources:
     limits:
       memory: "2Gi"  # Increase from 1Gi
   ```

3. **Enable connection limits:**
   ```java
   @Override
   public void afterConnectionEstablished(WebSocketSession session) {
       if (sessions.size() >= maxConnections) {
           session.close(CloseStatus.TRY_AGAIN_LATER);
           return;
       }
       // ... proceed
   }
   ```

### Issue: Event Hub consumer not starting

**Error:**
```
Event Hub connection string not configured. Consumer disabled.
```

**Check:**
```bash
echo $EVENTHUB_CONNECTION_STRING
```

**Verify connection string format:**
```
Endpoint=sb://YOUR-NAMESPACE.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=YOUR-KEY
```

---

## 🎯 Key Takeaways

### This Service Is:
✅ **Real-time distribution** hub for FX rates
✅ Event-driven (consumes from Event Hubs)
✅ Stateful (maintains WebSocket connections and subscriptions)
✅ Push-based (servers pushes updates to clients)
✅ Selective broadcasting (only to subscribed clients)

### This Service Is NOT:
❌ Fetching data from providers (that's rate-ingestion)
❌ Serving REST requests (that's fx-rates-api)
❌ Publishing to Event Hubs (that's rate-ingestion)
❌ Storing data (that's Cosmos DB)

### Data Flow Summary:

```
rate-ingestion-service
        ↓
  (publishes to)
        ↓
   Event Hubs: "fx-rates-updates"
        ↓
  (consumed by)
        ↓
  EventHubConsumer
        ↓
  SubscriptionService
        ↓
  WebSocket Broadcast
        ↓
  Connected Clients (Web/Mobile apps)
```

### Client Experience:

```
1. Connect:     ws://server:8082/ws/fx-rates
2. Subscribe:   { action: "subscribe", currencyPairs: ["EURUSD"] }
3. Receive:     Real-time updates every 5 seconds
4. Unsubscribe: { action: "unsubscribe", currencyPairs: ["EURUSD"] }
5. Disconnect:  Client or server closes connection
```

### Concurrency Handling:

```java
// Thread-safe collections
ConcurrentHashMap<String, WebSocketSession> sessions
ConcurrentHashMap<String, Set<String>> subscriptions
CopyOnWriteArraySet<String> per-subscription set

// Why?
- EventHubConsumer thread adds events
- WebSocket handler threads manage subscriptions
- Broadcast happens from EventHub thread
- All need concurrent access without locks!
```

---

## 📚 Related Files

- `../rate-ingestion-service/` - Publishes to Event Hubs
- `../fx-rates-api/` - REST API for rates
- `../common-lib/` - Shared models and events
- `../k8s/base/websocket-service-deployment.yaml` - K8s config

---

## 🔜 Enhancements

### 1. Redis Pub/Sub for Multi-Replica Setup

**Currently:** Each replica only broadcasts to its own WebSocket clients

**Future:** Use Redis to distribute events to all replicas

```java
@Service
public class RedisMessageBroker {

    @PostConstruct
    public void subscribe() {
        redisTemplate.convertAndSend("fx-rates-channel", event);
    }

    @RedisMessageListener(channel = "fx-rates-channel")
    public void onMessage(FxRateUpdatedEvent event) {
        subscriptionService.broadcastRateUpdate(event);
    }
}
```

### 2. Subscription Persistence

**Currently:** If service restarts, subscriptions are lost

**Future:** Store subscriptions in Redis

```java
@Service
public class PersistentSubscriptionService {

    public void subscribe(String sessionId, List<String> pairs) {
        // Store in Redis
        redisTemplate.opsForSet().add("subscriptions:" + sessionId, pairs);

        // Also keep in memory for fast access
        inMemorySubscriptions.put(sessionId, pairs);
    }

    @PostConstruct
    public void restoreSubscriptions() {
        // On startup, restore from Redis
        Set<String> sessionIds = redisTemplate.keys("subscriptions:*");
        // ... restore
    }
}
```

### 3. Authentication & Authorization

**Currently:** No authentication

**Future:** JWT-based authentication

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) {
    // Extract JWT from query params or headers
    String token = extractToken(session);

    // Validate token
    if (!jwtService.validate(token)) {
        session.close(CloseStatus.NOT_ACCEPTABLE);
        return;
    }

    // Store user info
    session.getAttributes().put("userId", jwtService.getUserId(token));
}
```

---

**Next:** Now you have all 3 microservices documented! Ready to get them running on Azure?
