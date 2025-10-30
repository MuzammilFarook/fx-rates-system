# FX Rates System - Deployment Quick Start

Get Azure infrastructure deployed and services running in **under 20 minutes**!

## 🚀 Prerequisites (5 minutes)

### **1. Install Azure CLI**

**Windows (PowerShell as Administrator):**
```powershell
# Method 1: Using winget
winget install -e --id Microsoft.AzureCLI

# Method 2: Download installer
# Visit: https://aka.ms/installazurecliwindows
```

**Verify Installation:**
```bash
az --version
```

### **2. Install Docker** (for local Redis)

Download from: https://www.docker.com/products/docker-desktop

**Verify:**
```bash
docker --version
```

### **3. Login to Azure**

```bash
az login

# Verify you're logged in
az account show

# Check your subscription
az account list --output table
```

---

## ⚡ Deploy Infrastructure (5-10 minutes)

### **Using Infrastructure as Code (Bicep) - Recommended**

```bash
# Navigate to infrastructure directory
cd infrastructure

# Deploy everything with one command
./deploy.sh
```

**What This Creates:**
- ✅ Resource Group: `fexco-fx-rates-rg`
- ✅ Cosmos DB (Serverless) - NoSQL database
- ✅ Event Hubs (Basic tier) - Event streaming
- ✅ All connection strings retrieved
- ✅ `.env` file generated automatically

**Expected Output:**
```
🚀 Deploying Azure Infrastructure
...
✅ Deployment completed!
🔑 Retrieving Connection Strings
...
📝 Generating .env File
✅ .env file created at: ../.env

📋 Next Steps:
1. Get Alpha Vantage API Key (30 seconds)
2. Start Local Redis
3. Load Environment Variables
4. Build and Run Services

💰 Estimated Monthly Cost: $12-15 (serverless Cosmos DB)
```

⏱️ **Deployment Time:** 5-10 minutes

---

## 🔑 Get Alpha Vantage API Key (30 seconds)

Alpha Vantage provides professional FX data with **real bid/ask spreads**.

### **Steps:**

1. Visit: https://www.alphavantage.co/support/#api-key
2. Enter your email
3. Receive API key instantly (it's free!)

### **Update .env File:**

```bash
# Go back to project root
cd ..

# Edit .env file (use any editor)
nano .env
# OR
notepad .env

# Update these two lines:
FX_PROVIDER_TYPE=alpha-vantage
ALPHA_VANTAGE_API_KEY=your-actual-api-key-here
```

**Alternative Providers:**
- `mock-reuters` - Realistic simulation, no API key needed (offline)
- `demo` - Free public API, calculated bid/ask (fallback)

---

## 🐳 Start Local Redis (1 minute)

```bash
# Start Redis container
docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine

# Verify it's running
docker ps | grep redis

# Expected output:
# CONTAINER ID   IMAGE           STATUS         PORTS
# abc123...      redis:7-alpine  Up 2 seconds   0.0.0.0:6379->6379/tcp
```

**Why local Redis?**
- ✅ FREE (saves $15-20/month vs Azure Redis)
- ✅ Instant startup
- ✅ Perfect for development and demos
- ✅ Can switch to Azure Redis in production by changing config

---

## 🏗️ Build and Run Services (5-10 minutes)

### **Step 1: Load Environment Variables**

```bash
# Make sure you're in project root (fx-rates-system/)
source .env

# Verify variables loaded
echo $COSMOS_ENDPOINT
echo $EVENTHUB_CONNECTION_STRING
echo $ALPHA_VANTAGE_API_KEY
```

### **Step 2: Build Common Library** (IMPORTANT!)

The common library must be built first as it's used by all services.

```bash
cd common-lib
mvn clean install
cd ..
```

**Expected Output:**
```
[INFO] BUILD SUCCESS
[INFO] Installing /path/to/fx-rates-common-lib-1.0.0.jar
```

### **Step 3: Run Automated Tests** (Optional but Recommended)

```bash
# Make script executable
chmod +x test-system.sh

# Run tests
./test-system.sh
```

This checks:
- ✅ Prerequisites (Java, Maven, Docker)
- ✅ Environment variables
- ✅ Redis connectivity
- ✅ Service builds
- ✅ Azure resource connectivity

### **Step 4: Start Services** (3 Separate Terminals)

You need **3 separate terminal windows/tabs**.

**Terminal 1: Rate Ingestion Service (Port 8081)**
```bash
cd rate-ingestion-service
mvn spring-boot:run
```

Wait for: `Started RateIngestionServiceApplication in X seconds`

**Terminal 2: FX Rates API (Port 8080)**
```bash
cd fx-rates-api
mvn spring-boot:run
```

Wait for: `Started FxRatesApiApplication in X seconds`

**Terminal 3: WebSocket Service (Port 8082)**
```bash
cd websocket-service
mvn spring-boot:run
```

Wait for: `Started WebSocketServiceApplication in X seconds`

---

## ✅ Verify Everything Works

### **Test 1: Health Checks**

```bash
# Rate Ingestion Service
curl http://localhost:8081/actuator/health

# FX Rates API
curl http://localhost:8080/actuator/health

# WebSocket Service
curl http://localhost:8082/actuator/health

# All should return: {"status":"UP"}
```

### **Test 2: Get FX Rates (REST API)**

```bash
# Get single rate
curl http://localhost:8080/api/v1/rates/EUR/USD | jq

# Expected output:
{
  "currencyPair": "EURUSD",
  "rate": 1.0850,
  "bid": 1.0848,
  "ask": 1.0852,
  "timestamp": "2024-01-15T10:30:00Z",
  "source": "Alpha Vantage",
  "confidenceScore": 0.95
}
```

### **Test 3: Supported Currency Pairs**

```bash
curl http://localhost:8080/api/v1/rates/supported | jq

# Expected: ["EURUSD", "GBPUSD", "USDJPY", ...]
```

### **Test 4: WebSocket Real-Time Updates**

```bash
# Option 1: Open test page in browser
# Navigate to: fx-rates-system/test-websocket.html
# Open in Chrome/Firefox

# Option 2: Command line test
npm install -g wscat
wscat -c ws://localhost:8082/ws/fx-rates

# Send subscription:
{"action":"subscribe","currencyPairs":["EURUSD","GBPUSD"]}

# You should see real-time rate updates every 5 seconds
```

---

## 🎉 Success Checklist

Verify all these steps:

- [ ] Azure CLI installed and logged in
- [ ] Azure infrastructure deployed (Cosmos DB + Event Hubs)
- [ ] `.env` file generated with connection strings
- [ ] Alpha Vantage API key obtained and added to `.env`
- [ ] Local Redis container running
- [ ] Environment variables loaded (`source .env`)
- [ ] common-lib built successfully
- [ ] All 3 services started without errors
- [ ] Health checks return `{"status":"UP"}`
- [ ] REST API returns FX rates with real bid/ask
- [ ] WebSocket shows real-time updates

**If all checked:** 🎉 **You're ready for the demo!**

---

## 📊 System Architecture

```
┌─────────────────┐
│  Alpha Vantage  │  (External FX Data Provider)
└────────┬────────┘
         │
         ↓
┌──────────────────────────┐
│ rate-ingestion-service   │  (Port 8081)
│ • Fetches rates (5s)     │
│ • Validates data         │
│ • Writes to Cosmos DB    │
│ • Publishes to Event Hub │
└────────┬─────────────────┘
         │
         ├──────→ ┌─────────────────┐
         │        │   Cosmos DB     │  (Azure - NoSQL)
         │        │  • Persistence  │
         │        │  • Serverless   │
         │        └────────┬────────┘
         │                 │
         ↓                 ↓
┌────────────────┐   ┌─────────────┐
│  Event Hubs    │   │ fx-rates-api│  (Port 8080)
│  • Streaming   │   │ • REST API  │
│  • Basic tier  │   │ • Caching   │
└───────┬────────┘   └──────┬──────┘
        │                   │
        ↓                   ↓
┌──────────────────┐  ┌──────────┐
│ websocket-service│  │  Redis   │  (Docker - Local)
│ • Real-time push │  │  • Cache │
│ • Port 8082      │  │  • 5s TTL│
└──────┬───────────┘  └──────────┘
       │
       ↓
┌────────────────┐
│  Browser       │
│  • WebSocket   │
│  • Real-time   │
└────────────────┘
```

---

## 🛑 Stop and Clean Up

### **Stop Services**

Press `Ctrl+C` in each of the 3 terminal windows.

### **Stop Local Redis**

```bash
docker stop fx-rates-redis
docker rm fx-rates-redis
```

### **Delete Azure Resources** (Stop All Costs!)

```bash
cd infrastructure
./destroy.sh

# Or skip confirmation:
./destroy.sh --yes
```

**What This Does:**
- 🗑️ Deletes entire resource group
- 💰 Stops ALL Azure charges
- 🔄 Can redeploy anytime with `./deploy.sh`
- ⏱️ Takes 3-5 minutes (runs in background)

**Verification:**
```bash
# Check deletion progress
az group list | grep fexco

# Should show nothing (deleted)
```

**Cost After Deletion:** $0/month

---

## 💰 Cost Management

### **Monthly Costs (When Running):**

| Resource | Tier | Cost/Month |
|----------|------|------------|
| Cosmos DB | Serverless | ~$1-2 (pay per request) |
| Event Hubs | Basic | ~$11 |
| Redis | Local Docker | **FREE** |
| **Total** | | **~$12-15/month** |

### **Cost for 1-Hour Demo:**
- Cosmos DB: ~$0.10 (few requests)
- Event Hubs: ~$0.01 (included in base)
- **Total: Less than $1!**

### **Free Tier Benefits:**
- $200 Azure credits (new accounts, 30 days)
- Cosmos DB: First 1000 RU/s free monthly
- Event Hubs: First 1M events free monthly

### **Best Practice for Interview Prep:**
```bash
# Deploy when practicing
./infrastructure/deploy.sh

# Test and practice (1-2 hours)
# ...

# Destroy when done
./infrastructure/destroy.sh --yes

# Redeploy next practice session
./infrastructure/deploy.sh
```

---

## 🎓 Interview Demo Tips

### **Before Interview:**

1. **Practice deployment** 2-3 times
   - Time yourself (should be 15-20 minutes total)
   - Get comfortable with commands

2. **Prepare talking points** (see below)

3. **Have backup plan**
   - If internet issues: Use `mock-reuters` provider (offline)
   - If time constraints: Have screenshots ready

### **Key Talking Points:**

**Infrastructure as Code:**
> "I used Bicep for IaC, which allows me to deploy the entire infrastructure with a single command and tear it down just as quickly. This is a production-ready DevOps practice that ensures repeatable deployments and excellent cost control."

**Microservices Architecture:**
> "The system consists of three independent Spring Boot microservices. Each service can be deployed, scaled, and maintained independently. This follows the single responsibility principle and enables horizontal scaling."

**Event-Driven Design:**
> "I used Azure Event Hubs for asynchronous communication between services. The rate ingestion service publishes events, and the WebSocket service consumes them. This loose coupling improves resilience and scalability."

**Data Freshness:**
> "The system guarantees 0-10 second data freshness through a combination of scheduled ingestion (every 5 seconds) and Redis caching (5-second TTL). Cache hits return in under 5ms, cache misses in under 50ms."

**Cost Optimization:**
> "I chose serverless Cosmos DB which eliminates fixed costs—we only pay for actual usage. Combined with Basic tier Event Hubs and local Redis for development, the system costs under $15/month. A 1-hour demo costs less than $1."

**Production Ready:**
> "The system includes circuit breakers, retry logic, comprehensive health checks, and a provider abstraction layer. We can easily integrate with enterprise providers like Reuters or Bloomberg by adding a new implementation—no code changes to the core services."

### **Demo Flow (10-15 minutes):**

**Part 1: Infrastructure (3 minutes)**
- Show `infrastructure/main.bicep`
- Explain resource choices
- Run `./deploy.sh` (or show pre-deployed)

**Part 2: Architecture (2 minutes)**
- Whiteboard/slides showing data flow
- Explain event-driven design
- Discuss scalability approach

**Part 3: Live Demo (5 minutes)**
- Show all 3 services running (logs)
- Test REST API with curl
- Show WebSocket real-time updates in browser
- Explain data flow as rates update

**Part 4: Code Walkthrough (3 minutes)**
- Show provider abstraction (`FxRateProvider` interface)
- Show circuit breaker configuration
- Show Redis caching implementation

**Part 5: Q&A (2 minutes)**
- Be ready to discuss scaling strategies
- Database choice rationale
- Security considerations
- Monitoring approach

---

## 🆘 Troubleshooting

### **Azure CLI Not Found**
```bash
# Windows: Install from
https://aka.ms/installazurecliwindows

# Restart terminal after installation
az --version
```

### **Azure Login Issues**
```bash
# Clear cached credentials
az account clear

# Login again
az login --use-device-code
```

### **Deployment Fails - Resource Name Exists**
```bash
# Cosmos DB and Event Hub names are globally unique
# The template uses uniqueString() to generate unique names
# If still fails, edit infrastructure/parameters.json
```

### **Services Won't Start**
```bash
# Check Redis is running
docker ps | grep redis

# Check environment variables
source .env
echo $COSMOS_ENDPOINT

# Rebuild common-lib
cd common-lib && mvn clean install
```

### **No Rates Returned**
```bash
# Wait 10-15 seconds for first ingestion cycle
# Check rate-ingestion-service logs:
# Should see: "Fetched X rates from Alpha Vantage"

# If using Alpha Vantage, check API key is correct
echo $ALPHA_VANTAGE_API_KEY

# Try mock-reuters provider (no API key needed)
FX_PROVIDER_TYPE=mock-reuters
```

### **WebSocket Not Connecting**
```bash
# Check websocket-service is running
curl http://localhost:8082/actuator/health

# Check browser console for errors
# Ensure correct URL: ws://localhost:8082/ws/fx-rates
# (not wss:// for local testing)
```

### **Maven Build Errors**
```bash
# Clean everything and rebuild
cd common-lib && mvn clean install
cd ../fx-rates-api && mvn clean package
cd ../rate-ingestion-service && mvn clean package
cd ../websocket-service && mvn clean package
```

---

## 📚 Additional Resources

- **Full Azure Setup Guide**: `AZURE-SETUP-GUIDE.md` (detailed manual steps)
- **Local Testing Guide**: `LOCAL-TESTING-GUIDE.md` (comprehensive testing)
- **IaC Documentation**: `infrastructure/README.md`
- **Provider Details**: `rate-ingestion-service/PROVIDER-IMPLEMENTATION.md`
- **Service Deep Dives**: Each service has `SERVICE-DEEP-DIVE.md`
- **Architecture**: See Knowledge folder for diagrams

---

## ✨ You're Ready!

You now have:
- ✅ Complete Azure infrastructure deployed
- ✅ All 3 microservices running and tested
- ✅ Real-time FX rates with professional data
- ✅ Production-ready architecture
- ✅ One-command deployment and cleanup

**Practice the demo 2-3 times, and you'll nail the interview!**

Good luck! 🚀

---

**Questions or Issues?**
- Check the troubleshooting section above
- Review `AZURE-SETUP-GUIDE.md` for detailed steps
- Verify all prerequisites are installed
- Ensure all environment variables are set
