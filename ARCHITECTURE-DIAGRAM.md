# FX Rates System - High-Level Architecture

## 🌍 Global Distribution & High-Traffic Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     GLOBAL PARTNERS (Worldwide Traffic)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │   Europe     │  │     Asia     │  │   Americas   │  │    Africa    │   │
│  │  Partners    │  │   Partners   │  │   Partners   │  │   Partners   │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AZURE FRONT DOOR / TRAFFIC MANAGER                       │
│              (Global Load Balancing & DDoS Protection)                      │
│  • Geographic routing                                                       │
│  • SSL termination                                                          │
│  • WAF (Web Application Firewall)                                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
                    ↓               ↓               ↓
        ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
        │   West Europe    │  │    East US       │  │  Southeast Asia  │
        │     Region       │  │    Region        │  │     Region       │
        └──────────────────┘  └──────────────────┘  └──────────────────┘

═══════════════════════════════════════════════════════════════════════════════
                        DETAILED ARCHITECTURE (Per Region)
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│                          EXTERNAL DATA SOURCES                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │Alpha Vantage │  │   Bloomberg  │  │   Reuters    │  │   Refinitiv  │   │
│  │(Professional)│  │  (Enterprise)│  │ (Enterprise) │  │  (Terminal)  │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
└─────────┼──────────────────┼──────────────────┼──────────────────┼──────────┘
          │                  │                  │                  │
          └──────────────────┴──────────────────┴──────────────────┘
                                    │
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                     RATE INGESTION SERVICE (Port 8081)                      │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  • Provider Abstraction Layer (Factory Pattern)                        │ │
│  │  • Scheduled Ingestion (Every 5 seconds)                               │ │
│  │  • Data Validation & Quality Scoring                                   │ │
│  │  • Circuit Breakers (Resilience4j)                                     │ │
│  │  • Retry Logic with Exponential Backoff                                │ │
│  │  • Automatic Provider Fallback                                         │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│  Instances: 2-10 (Auto-scaled based on load)                               │
│  Scaling Trigger: CPU > 70%, Memory > 80%                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                    │                               │
                    │ Write                         │ Publish Event
                    ↓                               ↓
    ┌───────────────────────────┐    ┌──────────────────────────────┐
    │    AZURE COSMOS DB        │    │    AZURE EVENT HUBS          │
    │   (Serverless NoSQL)      │    │   (Standard Tier)            │
    │                           │    │                              │
    │ • Multi-region replication│    │ • 2 Partitions              │
    │ • Session consistency     │    │ • 1 day retention           │
    │ • Partition: /currencyPair│    │ • Consumer Groups:          │
    │ • Automatic indexing      │    │   - websocket-service       │
    │ • TTL support             │    │   - analytics-service       │
    │ • Point-in-time backup    │    │   - audit-service           │
    │                           │    │                              │
    │ Write Pattern:            │    │ Throughput: 1000 events/sec │
    │ • ~8 writes per 5s        │    │ Latency: < 10ms             │
    └───────────────────────────┘    └──────────────────────────────┘
                    │                               │
                    │ Read                          │ Consume
                    ↓                               ↓
    ┌───────────────────────────┐    ┌──────────────────────────────┐
    │  FX RATES API (Port 8080) │    │ WEBSOCKET SERVICE (8082)     │
    │                           │    │                              │
    │ • REST API endpoints      │    │ • STOMP protocol            │
    │ • Health checks           │    │ • Real-time broadcasting    │
    │ • Circuit breakers        │    │ • Connection pooling        │
    │ • Request validation      │    │ • Subscription management   │
    │ • OpenAPI/Swagger docs    │    │ • Heartbeat monitoring      │
    │                           │    │                              │
    │ Instances: 3-20           │    │ Instances: 2-15             │
    │ Target: < 50ms response   │    │ Connections: 10k per pod    │
    └───────┬───────────────────┘    └──────────────────────────────┘
            │ Cache                              │
            ↓                                    ↓
    ┌───────────────────────────┐         [WebSocket Clients]
    │   AZURE REDIS CACHE       │         • Trading platforms
    │   (Premium Tier)          │         • Mobile apps
    │                           │         • Web dashboards
    │ • 5 second TTL            │         • Partner systems
    │ • LRU eviction policy     │
    │ • Geo-replication         │
    │ • 99.9% SLA               │
    │ • SSL/TLS enabled         │
    │                           │
    │ Hit Rate Target: > 95%    │
    │ Response Time: < 5ms      │
    └───────────────────────────┘
            │
            ↓
    [REST API Clients]
    • Trading platforms
    • Analytics systems
    • Partner integrations
    • Mobile applications

═══════════════════════════════════════════════════════════════════════════════
                        KUBERNETES CLUSTER (AKS)
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│                         CONTAINER ORCHESTRATION                             │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  Horizontal Pod Autoscaler (HPA)                                       │ │
│  │  • Scale 2-20 pods based on CPU/Memory                                 │ │
│  │  • Target: 70% CPU utilization                                         │ │
│  │  └───────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                          │ │
│  │  KEDA (Kubernetes Event-Driven Autoscaling)                             │ │
│  │  • Scale based on Event Hub lag                                         │ │
│  │  • Scale based on Redis queue depth                                     │ │
│  │  └───────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                          │ │
│  │  Network Policies                                                        │ │
│  │  • Service mesh (Istio/Linkerd)                                         │ │
│  │  • mTLS between services                                                │ │
│  │  • Rate limiting per client                                             │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════
                    MONITORING & OBSERVABILITY
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│                      AZURE APPLICATION INSIGHTS                             │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  Metrics Tracked:                                                       │ │
│  │  • Request rate (requests/second)                                       │ │
│  │  • Response time (p50, p95, p99)                                        │ │
│  │  • Error rate (%)                                                       │ │
│  │  • Cache hit rate (%)                                                   │ │
│  │  • External provider latency                                            │ │
│  │  • Event Hub throughput                                                 │ │
│  │  • WebSocket connection count                                           │ │
│  │  • Cosmos DB RU consumption                                             │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│  Alerts Configured:                                                         │
│  • Response time > 100ms (P95)                                              │
│  • Error rate > 1%                                                          │
│  • Cache hit rate < 90%                                                     │
│  • Circuit breaker open                                                     │
│  • Pod restart count > 3                                                    │
└─────────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════
                    DATA FLOW & LATENCY
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│  1. INGESTION PATH (Every 5 seconds)                                        │
│     External Provider → Ingestion Service → Cosmos DB + Event Hub           │
│     Latency: 200-500ms (depends on provider)                                │
│                                                                             │
│  2. REST API PATH (On-demand query)                                         │
│     Client → API Gateway → FX Rates API → Redis (cache hit)                │
│     Latency: < 5ms (cache hit) | < 50ms (cache miss → Cosmos DB)           │
│                                                                             │
│  3. WEBSOCKET PATH (Real-time push)                                         │
│     Event Hub → WebSocket Service → Connected Clients                       │
│     Latency: < 100ms (from ingestion to client notification)                │
│                                                                             │
│  DATA FRESHNESS GUARANTEE:                                                  │
│  • Maximum staleness: 10 seconds (5s ingestion + 5s cache TTL)             │
│  • Typical staleness: 2-5 seconds                                           │
│  • Real-time updates: < 1 second via WebSocket                              │
└─────────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════
                    SCALABILITY & PERFORMANCE
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│  THROUGHPUT CAPACITY (Per Region):                                          │
│                                                                             │
│  FX Rates API:                                                              │
│  • 10,000 requests/second (with caching)                                    │
│  • 500 requests/second (without cache, direct Cosmos DB)                    │
│  • 95%+ cache hit rate under normal load                                    │
│                                                                             │
│  WebSocket Service:                                                         │
│  • 100,000+ concurrent connections                                          │
│  • 10,000 messages/second broadcast rate                                    │
│  • Sub-second latency for rate updates                                     │
│                                                                             │
│  Rate Ingestion Service:                                                    │
│  • 8 currency pairs per 5 seconds (currently)                               │
│  • Scalable to 100+ pairs with multiple providers                           │
│  • Parallel fetching with configurable concurrency                          │
│                                                                             │
│  SCALING STRATEGIES:                                                        │
│  1. Horizontal: Add more pods (2 → 20 per service)                          │
│  2. Vertical: Increase pod resources (CPU/Memory)                           │
│  3. Geographic: Deploy to multiple Azure regions                            │
│  4. Caching: Multi-tier (Redis → CDN for static data)                       │
│  5. Database: Read replicas + sharding by currency pair                     │
└─────────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════
                    RESILIENCE & HIGH AVAILABILITY
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│  FAILURE HANDLING:                                                          │
│                                                                             │
│  1. Provider Failures:                                                      │
│     • Circuit breaker (open after 50% failure rate)                         │
│     • Automatic fallback to alternate providers                             │
│     • Retry with exponential backoff                                        │
│     • Cached data served during outages                                     │
│                                                                             │
│  2. Service Failures:                                                       │
│     • Health checks (liveness & readiness probes)                           │
│     • Automatic pod restart                                                 │
│     • Load balancer removes unhealthy instances                             │
│     • Rolling updates (zero downtime)                                       │
│                                                                             │
│  3. Database Failures:                                                      │
│     • Multi-region active-passive replication                               │
│     • Automatic failover (< 30 seconds)                                     │
│     • Point-in-time restore (7-35 days)                                     │
│     • Cache serves stale data during outage                                 │
│                                                                             │
│  4. Network Failures:                                                       │
│     • Retry logic with idempotency keys                                     │
│     • Client-side timeout handling                                          │
│     • Geographic routing to healthy regions                                 │
│                                                                             │
│  SLA TARGETS:                                                               │
│  • Availability: 99.95% (< 4.5 hours downtime/year)                         │
│  • Data durability: 99.999999999% (11 nines)                                │
│  • RTO (Recovery Time Objective): < 5 minutes                               │
│  • RPO (Recovery Point Objective): < 1 minute                               │
└─────────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════
                    SECURITY
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│  AUTHENTICATION & AUTHORIZATION:                                            │
│  • API Key authentication for partners                                      │
│  • OAuth 2.0 / JWT for user authentication                                  │
│  • Azure AD integration for internal services                               │
│  • Rate limiting per API key (1000 req/min default)                         │
│                                                                             │
│  ENCRYPTION:                                                                │
│  • TLS 1.3 for all external communications                                  │
│  • mTLS between microservices                                               │
│  • Encryption at rest (Cosmos DB, Redis, Event Hubs)                        │
│  • Azure Key Vault for secrets management                                   │
│                                                                             │
│  NETWORK SECURITY:                                                          │
│  • Virtual Network (VNet) isolation                                         │
│  • Private endpoints for Azure services                                     │
│  • Network security groups (NSGs)                                           │
│  • DDoS protection (Azure Front Door)                                       │
│  • Web Application Firewall (WAF)                                           │
└─────────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════
                    COST OPTIMIZATION
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│  MONTHLY COST ESTIMATE (Production - Single Region):                        │
│                                                                             │
│  • Cosmos DB (Serverless):        $50-100  (pay per RU consumed)           │
│  • Event Hubs (Standard):         $50      (1 throughput unit)             │
│  • Redis Cache (Premium P1):      $150     (6 GB, geo-replication)         │
│  • AKS Cluster:                   $200     (3 nodes, Standard_D4s_v3)      │
│  • Application Insights:          $50      (5 GB data/month)               │
│  • Azure Front Door:              $35      (base + routing)                │
│  • Bandwidth:                     $50      (estimated)                     │
│  ─────────────────────────────────────────────────────────────────────     │
│  TOTAL:                           ~$585-635/month per region                │
│                                                                             │
│  SCALING TO MULTIPLE REGIONS:                                               │
│  • 3 regions (US, EU, ASIA): ~$1,800-2,000/month                            │
│                                                                             │
│  COST OPTIMIZATION STRATEGIES:                                              │
│  • Use Serverless Cosmos DB (pay per use, not provisioned RU)              │
│  • Scale down during off-peak hours (nights, weekends)                      │
│  • Use Azure Reserved Instances (40% discount)                              │
│  • Spot instances for non-critical workloads                                │
│  • Aggressive caching to reduce database costs                              │
└─────────────────────────────────────────────────────────────────────────────┘
