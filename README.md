# FX Rates System - Fexco Principal Engineer Implementation

A production-ready, globally distributed FX rates system built with Spring Boot microservices and Azure cloud services.

## 🏗️ Architecture Overview

This implementation follows the architecture designed for the Fexco Principal Engineer take-home assignment, featuring:

- **Multi-region deployment** with Azure Front Door for global distribution
- **Auto-scaling microservices** on Azure Kubernetes Service (AKS)
- **Real-time event streaming** with Azure Event Hubs
- **Multi-layer caching** with Azure Cache for Redis
- **Global data persistence** with Azure Cosmos DB and Azure SQL
- **Comprehensive monitoring** with Application Insights

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│  Global Partners → Azure Front Door → API Management        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  AKS Cluster (Multi-AZ)                              │  │
│  │                                                       │  │
│  │  • fx-rates-api (REST/gRPC)           [HPA scaling] │  │
│  │  • websocket-service (Real-time)      [HPA scaling] │  │
│  │  • rate-ingestion-service             [KEDA scaling]│  │
│  └──────────────────────────────────────────────────────┘  │
│              ↓                    ↓                          │
│    ┌──────────────┐    ┌──────────────────────┐            │
│    │ Event Hubs   │    │ Redis (Geo-replicated)│            │
│    │ (Kafka API)  │    │ Cache + Pub/Sub       │            │
│    └──────────────┘    └──────────────────────┘            │
│              ↓                    ↓                          │
│    ┌──────────────────────────────────────────┐            │
│    │ Cosmos DB (Current)  │  Azure SQL (History)│           │
│    └──────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

## 📦 Project Structure

```
fx-rates-system/
├── common-lib/                    # Shared models, DTOs, events
├── fx-rates-api/                  # REST API service
│   ├── controller/                # REST controllers
│   ├── service/                   # Business logic
│   ├── repository/                # Cosmos DB integration
│   └── config/                    # Redis, OpenAPI config
├── rate-ingestion-service/        # FX rate ingestion
│   ├── client/                    # External provider clients
│   ├── publisher/                 # Event Hub publisher
│   ├── scheduler/                 # Scheduled jobs
│   └── service/                   # Validation & enrichment
├── websocket-service/             # Real-time WebSocket push
│   ├── handler/                   # WebSocket handlers
│   ├── consumer/                  # Event Hub consumer
│   └── service/                   # Subscription management
├── k8s/                           # Kubernetes manifests
│   ├── base/                      # Base configurations
│   └── overlays/                  # Environment-specific
├── docker-compose.yml             # Local development
└── README.md                      # This file
```

## 🚀 Quick Start

### Prerequisites

- **Java 17+**
- **Maven 3.9+**
- **Docker & Docker Compose**
- **Azure Account** with credits
- **kubectl** (for Kubernetes deployment)

### Local Development with Docker Compose

1. **Clone and navigate to the project:**
   ```bash
   cd fx-rates-system
   ```

2. **Configure environment variables:**
   ```bash
   cp .env.example .env
   # Edit .env with your Azure credentials
   ```

3. **Build and run all services:**
   ```bash
   docker-compose up --build
   ```

4. **Access the services:**
   - FX Rates API: http://localhost:8080/api/v1/swagger-ui.html
   - Rate Ingestion: http://localhost:8081/actuator
   - WebSocket: ws://localhost:8082/ws/fx-rates

### Build Locally (Without Docker)

1. **Build all modules:**
   ```bash
   mvn clean install
   ```

2. **Run services individually:**
   ```bash
   # Terminal 1 - FX Rates API
   cd fx-rates-api
   mvn spring-boot:run

   # Terminal 2 - Rate Ingestion Service
   cd rate-ingestion-service
   mvn spring-boot:run

   # Terminal 3 - WebSocket Service
   cd websocket-service
   mvn spring-boot:run
   ```

## ☁️ Azure Deployment

### 1. Set Up Azure Resources

#### Create Resource Group
```bash
az group create --name fexco-fx-rates-rg --location eastus
```

#### Deploy Azure Cosmos DB
```bash
az cosmosdb create \
  --name fexco-fx-rates-cosmos \
  --resource-group fexco-fx-rates-rg \
  --default-consistency-level Session \
  --locations regionName=eastus failoverPriority=0

# Create database and container
az cosmosdb sql database create \
  --account-name fexco-fx-rates-cosmos \
  --resource-group fexco-fx-rates-rg \
  --name fxrates

az cosmosdb sql container create \
  --account-name fexco-fx-rates-cosmos \
  --database-name fxrates \
  --name rates \
  --partition-key-path "/currencyPair" \
  --throughput 1000
```

#### Deploy Azure Event Hubs
```bash
az eventhubs namespace create \
  --name fexco-fx-rates-eh \
  --resource-group fexco-fx-rates-rg \
  --location eastus \
  --sku Standard

az eventhubs eventhub create \
  --name fx-rates-updates \
  --namespace-name fexco-fx-rates-eh \
  --resource-group fexco-fx-rates-rg \
  --partition-count 4
```

#### Deploy Azure Cache for Redis
```bash
az redis create \
  --name fexco-fx-rates-redis \
  --resource-group fexco-fx-rates-rg \
  --location eastus \
  --sku Standard \
  --vm-size C1
```

#### Deploy AKS Cluster
```bash
az aks create \
  --name fexco-fx-rates-aks \
  --resource-group fexco-fx-rates-rg \
  --node-count 3 \
  --node-vm-size Standard_D2s_v3 \
  --enable-cluster-autoscaler \
  --min-count 3 \
  --max-count 10 \
  --enable-addons monitoring

# Get credentials
az aks get-credentials \
  --name fexco-fx-rates-aks \
  --resource-group fexco-fx-rates-rg
```

### 2. Deploy to Kubernetes

#### Build and Push Docker Images
```bash
# Build images
docker build -t fexco/fx-rates-api:latest -f fx-rates-api/Dockerfile .
docker build -t fexco/rate-ingestion-service:latest -f rate-ingestion-service/Dockerfile .
docker build -t fexco/websocket-service:latest -f websocket-service/Dockerfile .

# Push to Azure Container Registry (ACR)
az acr create --resource-group fexco-fx-rates-rg --name fexcofxratesacr --sku Basic
az acr login --name fexcofxratesacr

docker tag fexco/fx-rates-api:latest fexcofxratesacr.azurecr.io/fx-rates-api:latest
docker push fexcofxratesacr.azurecr.io/fx-rates-api:latest
# ... repeat for other services
```

#### Create Kubernetes Secrets
```bash
kubectl create secret generic azure-secrets \
  --from-literal=cosmos-endpoint="https://your-cosmos.documents.azure.com:443/" \
  --from-literal=cosmos-key="your-cosmos-key" \
  --from-literal=eventhub-connection-string="your-eventhub-connection" \
  --from-literal=appinsights-key="your-appinsights-key"
```

#### Deploy Services
```bash
kubectl apply -f k8s/base/redis-deployment.yaml
kubectl apply -f k8s/base/fx-rates-api-deployment.yaml
kubectl apply -f k8s/base/rate-ingestion-deployment.yaml
kubectl apply -f k8s/base/websocket-deployment.yaml
```

#### Install KEDA for Event-Driven Autoscaling
```bash
helm repo add kedacore https://kedacore.github.io/charts
helm repo update
helm install keda kedacore/keda --namespace keda --create-namespace
```

## 📊 Monitoring and Observability

### Application Insights Integration

All services are instrumented with Azure Application Insights for:
- Distributed tracing
- Performance metrics
- Exception tracking
- Custom events

Access dashboards at: https://portal.azure.com → Application Insights

### Health Checks

- **FX Rates API**: `GET http://localhost:8080/actuator/health`
- **Rate Ingestion**: `GET http://localhost:8081/actuator/health`
- **WebSocket**: `GET http://localhost:8082/actuator/health`

### Metrics

All services expose Prometheus metrics at `/actuator/prometheus`

## 🔌 API Documentation

### REST API (FX Rates API)

**Swagger UI**: http://localhost:8080/api/v1/swagger-ui.html

#### Get Single FX Rate
```bash
curl http://localhost:8080/api/v1/rates/EUR/USD
```

#### Get Batch FX Rates
```bash
curl -X POST http://localhost:8080/api/v1/rates/batch \
  -H "Content-Type: application/json" \
  -d '{
    "currencyPairs": ["EURUSD", "GBPUSD", "USDJPY"]
  }'
```

#### Get Historical Rates
```bash
curl "http://localhost:8080/api/v1/rates/history/EUR/USD?limit=10"
```

### WebSocket API

**Connect to WebSocket:**
```javascript
const ws = new WebSocket('ws://localhost:8082/ws/fx-rates');

// Subscribe to currency pairs
ws.send(JSON.stringify({
  action: 'subscribe',
  currencyPairs: ['EURUSD', 'GBPUSD']
}));

// Receive real-time updates
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Rate update:', data);
};
```

## 🧪 Testing

### Run Unit Tests
```bash
mvn test
```

### Run Integration Tests
```bash
mvn verify
```

### Load Testing
Use Apache JMeter or k6 for load testing:
```bash
k6 run --vus 100 --duration 60s load-test.js
```

## 📈 Scaling Configuration

### Horizontal Pod Autoscaler (HPA)

- **FX Rates API**: 3-20 replicas (CPU: 70%, Memory: 80%)
- **WebSocket Service**: 3-15 replicas (CPU: 70%, Memory: 80%)

### KEDA Event-Driven Autoscaling

- **Rate Ingestion Service**: 2-10 replicas based on Event Hub lag

## 🔒 Security Features

- ✅ OAuth 2.0 authentication via API Management
- ✅ TLS 1.3 encryption in transit
- ✅ Encryption at rest (Azure services)
- ✅ Network isolation with VNet
- ✅ Secrets management with Azure Key Vault
- ✅ Container security with non-root users

## 🎯 Performance Targets

| Metric | Target | Implementation |
|--------|--------|----------------|
| Availability | 99.99% SLA | Multi-AZ, circuit breakers, auto-failover |
| Latency (p99) | <100ms | Redis caching, CDN, multi-region |
| Cache Hit Ratio | >95% | Redis with 5s TTL, cache warming |
| Throughput | Elastic | HPA + KEDA autoscaling |

## 💰 Cost Optimization

- Auto-scaling reduces costs during low traffic
- Redis caching reduces Cosmos DB RU consumption
- Cosmos DB serverless mode for variable workloads
- Reserved instances for baseline capacity

## 📝 License

This project was created for the Fexco Principal Engineer interview process.

## 👤 Author

Built as part of the Fexco Principal Engineer assessment.

---

## 🎯 Interview Demo Checklist

- [ ] Azure resources provisioned
- [ ] Services deployed to AKS
- [ ] Application Insights configured
- [ ] Demonstrate REST API calls
- [ ] Show WebSocket real-time updates
- [ ] Display auto-scaling in action
- [ ] Show monitoring dashboards
- [ ] Explain architecture decisions
