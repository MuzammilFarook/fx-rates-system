# Azure Infrastructure Setup Guide

## 🎯 Overview

This guide helps you set up the complete Azure infrastructure for the FX Rates System on your **free Azure account**.

**What We'll Create:**
1. ✅ Azure Cosmos DB (NoSQL database)
2. ✅ Azure Event Hubs (event streaming)
3. ✅ Azure Cache for Redis (caching - optional, use local Docker)
4. ✅ Azure Application Insights (monitoring - optional)

**Estimated Monthly Cost:**
- **With IaC (Recommended)**: ~$12-15/month (serverless Cosmos + Basic Event Hub + local Redis)
- **Free Tier Credits**: $200 for 30 days
- **For 1-hour Demo**: Less than $1!

---

## 🚀 Quick Start (Recommended)

### **Option 1: Infrastructure as Code (IaC) - FASTEST! ⚡**

This is the **recommended approach** for interview preparation. Uses Bicep templates to create/destroy everything with single commands.

**Advantages:**
- ✅ **One command** to create everything (5-10 minutes)
- ✅ **One command** to destroy everything (stop costs)
- ✅ **Repeatable** - Deploy/destroy as many times as needed
- ✅ **Cost-optimized** - Serverless Cosmos DB, Basic Event Hub
- ✅ **Production-ready** - Infrastructure as Code shows professional approach

```bash
# Navigate to infrastructure directory
cd infrastructure

# Deploy everything (generates .env file automatically)
./deploy.sh

# When done, destroy everything to stop costs
./destroy.sh
```

**That's it!** Skip to [Next Steps](#-next-steps-after-deployment) after deployment completes.

📖 **Full IaC Documentation**: See `infrastructure/README.md`

---

### **Option 2: Manual Setup (Azure CLI)**

If you prefer step-by-step manual setup, continue reading this guide below.

---

## 📋 Prerequisites

### **1. Azure Account**

If you don't have one:
```bash
# Visit: https://azure.microsoft.com/free/
# Sign up with your email
# Get $200 free credit for 30 days
```

### **2. Azure CLI**

**Windows (PowerShell as Administrator):**
```powershell
# Install Azure CLI
winget install -e --id Microsoft.AzureCLI

# Or download from:
# https://aka.ms/installazurecliwindows
```

**Verify Installation:**
```bash
az --version
```

### **3. Login to Azure**

```bash
# Login
az login

# Verify subscription
az account show

# List subscriptions (if you have multiple)
az account list --output table

# Set default subscription (if needed)
az account set --subscription "YOUR_SUBSCRIPTION_NAME"
```

---

## 🏗️ Infrastructure Setup

### **Step 1: Create Resource Group**

All resources will be created in this group for easy management.

```bash
# Set variables
RESOURCE_GROUP="fexco-fx-rates-rg"
LOCATION="eastus"  # Or "westeurope", "uksouth", etc.

# Create resource group
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION

echo "✅ Resource group created: $RESOURCE_GROUP"
```

**Expected Output:**
```json
{
  "id": "/subscriptions/.../resourceGroups/fexco-fx-rates-rg",
  "location": "eastus",
  "name": "fexco-fx-rates-rg",
  "properties": {
    "provisioningState": "Succeeded"
  }
}
```

---

### **Step 2: Create Azure Cosmos DB**

**Option A: Serverless (Recommended for Dev/Test - Pay per use)**

```bash
# Set variables
COSMOS_ACCOUNT_NAME="fexco-cosmosdb-$(openssl rand -hex 4)"
COSMOS_DATABASE="fxrates"
COSMOS_CONTAINER="rates"

# Create Cosmos DB account (serverless)
az cosmosdb create \
  --name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --locations regionName=$LOCATION \
  --capabilities EnableServerless \
  --default-consistency-level Session

echo "✅ Cosmos DB account created: $COSMOS_ACCOUNT_NAME"

# Create database
az cosmosdb sql database create \
  --account-name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --name $COSMOS_DATABASE

echo "✅ Database created: $COSMOS_DATABASE"

# Create container with partition key
az cosmosdb sql container create \
  --account-name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --database-name $COSMOS_DATABASE \
  --name $COSMOS_CONTAINER \
  --partition-key-path "/currencyPair"

echo "✅ Container created: $COSMOS_CONTAINER"

# Get connection details
COSMOS_ENDPOINT=$(az cosmosdb show \
  --name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "documentEndpoint" -o tsv)

COSMOS_KEY=$(az cosmosdb keys list \
  --name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "primaryMasterKey" -o tsv)

echo ""
echo "📋 Cosmos DB Connection Details:"
echo "COSMOS_ENDPOINT=$COSMOS_ENDPOINT"
echo "COSMOS_KEY=$COSMOS_KEY"
echo ""
```

**Cost:** ~$0.25 per million requests (free tier available)

---

**Option B: Provisioned Throughput (Predictable cost)**

```bash
# For production with predictable load
az cosmosdb create \
  --name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --locations regionName=$LOCATION \
  --default-consistency-level Session

# Create database with shared throughput (400 RU/s minimum)
az cosmosdb sql database create \
  --account-name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --name $COSMOS_DATABASE \
  --throughput 400

# Create container
az cosmosdb sql container create \
  --account-name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --database-name $COSMOS_DATABASE \
  --name $COSMOS_CONTAINER \
  --partition-key-path "/currencyPair"
```

**Cost:** ~$25/month (400 RU/s)

---

### **Step 3: Create Azure Event Hubs**

```bash
# Set variables
EVENTHUB_NAMESPACE="fexco-eventhub-$(openssl rand -hex 4)"
EVENTHUB_NAME="fx-rates-updates"

# Create Event Hubs namespace (Basic tier)
az eventhubs namespace create \
  --name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Basic \
  --capacity 1

echo "✅ Event Hubs namespace created: $EVENTHUB_NAMESPACE"

# Create Event Hub
az eventhubs eventhub create \
  --name $EVENTHUB_NAME \
  --namespace-name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --partition-count 2 \
  --message-retention 1

echo "✅ Event Hub created: $EVENTHUB_NAME"

# Create consumer group for websocket-service
az eventhubs eventhub consumer-group create \
  --eventhub-name $EVENTHUB_NAME \
  --namespace-name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --name websocket-service

echo "✅ Consumer group created: websocket-service"

# Get connection string
EVENTHUB_CONNECTION_STRING=$(az eventhubs namespace authorization-rule keys list \
  --namespace-name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --name RootManageSharedAccessKey \
  --query "primaryConnectionString" -o tsv)

echo ""
echo "📋 Event Hubs Connection Details:"
echo "EVENTHUB_NAMESPACE=$EVENTHUB_NAMESPACE"
echo "EVENTHUB_CONNECTION_STRING=$EVENTHUB_CONNECTION_STRING"
echo ""
```

**Cost:** Basic tier ~$10-15/month

---

### **Step 4: Create Azure Cache for Redis**

```bash
# Set variables
REDIS_NAME="fexco-redis-$(openssl rand -hex 4)"

# Create Redis Cache (Basic C0 - 250MB)
az redis create \
  --name $REDIS_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Basic \
  --vm-size c0

echo "✅ Redis Cache created: $REDIS_NAME"
echo "⏳ This takes ~15-20 minutes to provision..."

# Wait for provisioning
az redis show \
  --name $REDIS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "provisioningState"

# Get Redis connection details
REDIS_HOST=$(az redis show \
  --name $REDIS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "hostName" -o tsv)

REDIS_KEY=$(az redis list-keys \
  --name $REDIS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "primaryKey" -o tsv)

echo ""
echo "📋 Redis Connection Details:"
echo "REDIS_HOST=$REDIS_HOST"
echo "REDIS_PORT=6380"
echo "REDIS_KEY=$REDIS_KEY"
echo "REDIS_SSL_ENABLED=true"
echo ""
```

**Cost:** Basic C0 ~$17/month

**Alternative: Use Local Redis for Dev:**
```bash
# Run Redis locally (free!)
docker run -d -p 6379:6379 redis:7-alpine
```

---

### **Step 5: Create Application Insights (Optional)**

```bash
# Set variables
APPINSIGHTS_NAME="fexco-appinsights"

# Create Application Insights
az monitor app-insights component create \
  --app $APPINSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --kind web

# Get instrumentation key
APPINSIGHTS_KEY=$(az monitor app-insights component show \
  --app $APPINSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "instrumentationKey" -o tsv)

echo ""
echo "📋 Application Insights Details:"
echo "APPINSIGHTS_INSTRUMENTATIONKEY=$APPINSIGHTS_KEY"
echo ""
```

**Cost:** Free tier: 5GB/month, then $2.30/GB

---

## 📊 Cost Summary

| Resource | Tier | Monthly Cost | Notes |
|----------|------|--------------|-------|
| **Cosmos DB** | Serverless | ~$0-5 | Pay per request, free tier |
| **Event Hubs** | Basic | ~$10-15 | 1M events/day included |
| **Redis Cache** | Basic C0 | ~$17 | Can use local Redis for dev |
| **App Insights** | Free | $0 | First 5GB free |
| **TOTAL** | | **~$30-40/month** | With free tier optimizations |

**💡 Cost Saving Tips:**
- Use **serverless Cosmos DB** for dev/test
- Use **local Redis** during development
- Delete resources when not in use
- Use **Azure free credits** ($200 for first 30 days)

---

## 🔧 Complete Setup Script

Save this as `setup-azure.sh`:

```bash
#!/bin/bash

# Azure Infrastructure Setup for FX Rates System
# Run: ./setup-azure.sh

set -e  # Exit on error

echo "🚀 Starting Azure Infrastructure Setup..."
echo ""

# Variables
RESOURCE_GROUP="fexco-fx-rates-rg"
LOCATION="eastus"
COSMOS_ACCOUNT_NAME="fexco-cosmosdb-$(openssl rand -hex 4)"
COSMOS_DATABASE="fxrates"
COSMOS_CONTAINER="rates"
EVENTHUB_NAMESPACE="fexco-eventhub-$(openssl rand -hex 4)"
EVENTHUB_NAME="fx-rates-updates"
REDIS_NAME="fexco-redis-$(openssl rand -hex 4)"
APPINSIGHTS_NAME="fexco-appinsights"

# 1. Create Resource Group
echo "1️⃣  Creating Resource Group..."
az group create --name $RESOURCE_GROUP --location $LOCATION
echo "✅ Resource group created"
echo ""

# 2. Create Cosmos DB
echo "2️⃣  Creating Cosmos DB (this takes ~5 minutes)..."
az cosmosdb create \
  --name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --locations regionName=$LOCATION \
  --capabilities EnableServerless \
  --default-consistency-level Session

az cosmosdb sql database create \
  --account-name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --name $COSMOS_DATABASE

az cosmosdb sql container create \
  --account-name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --database-name $COSMOS_DATABASE \
  --name $COSMOS_CONTAINER \
  --partition-key-path "/currencyPair"

echo "✅ Cosmos DB created"
echo ""

# 3. Create Event Hubs
echo "3️⃣  Creating Event Hubs..."
az eventhubs namespace create \
  --name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Basic

az eventhubs eventhub create \
  --name $EVENTHUB_NAME \
  --namespace-name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --partition-count 2 \
  --message-retention 1

az eventhubs eventhub consumer-group create \
  --eventhub-name $EVENTHUB_NAME \
  --namespace-name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --name websocket-service

echo "✅ Event Hubs created"
echo ""

# 4. Skip Redis for now (use local Docker)
echo "4️⃣  Skipping Azure Redis (use local Docker instead for dev)"
echo "   Run: docker run -d -p 6379:6379 redis:7-alpine"
echo ""

# 5. Create Application Insights
echo "5️⃣  Creating Application Insights..."
az monitor app-insights component create \
  --app $APPINSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --kind web

echo "✅ Application Insights created"
echo ""

# Get all connection strings
echo "🔑 Retrieving connection strings..."
echo ""

COSMOS_ENDPOINT=$(az cosmosdb show --name $COSMOS_ACCOUNT_NAME --resource-group $RESOURCE_GROUP --query "documentEndpoint" -o tsv)
COSMOS_KEY=$(az cosmosdb keys list --name $COSMOS_ACCOUNT_NAME --resource-group $RESOURCE_GROUP --query "primaryMasterKey" -o tsv)
EVENTHUB_CONNECTION_STRING=$(az eventhubs namespace authorization-rule keys list --namespace-name $EVENTHUB_NAMESPACE --resource-group $RESOURCE_GROUP --name RootManageSharedAccessKey --query "primaryConnectionString" -o tsv)
APPINSIGHTS_KEY=$(az monitor app-insights component show --app $APPINSIGHTS_NAME --resource-group $RESOURCE_GROUP --query "instrumentationKey" -o tsv)

# Save to .env file
cat > .env << EOF
# Azure Connection Strings
# Generated on: $(date)

# Cosmos DB
COSMOS_ENDPOINT=$COSMOS_ENDPOINT
COSMOS_KEY=$COSMOS_KEY
COSMOS_DATABASE=fxrates

# Event Hubs
EVENTHUB_CONNECTION_STRING=$EVENTHUB_CONNECTION_STRING
EVENTHUB_NAMESPACE=$EVENTHUB_NAMESPACE
EVENTHUB_TOPIC=fx-rates-updates
EVENTHUB_CONSUMER_GROUP=websocket-service

# Redis (local Docker)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_SSL_ENABLED=false

# Application Insights
APPINSIGHTS_INSTRUMENTATIONKEY=$APPINSIGHTS_KEY
APPINSIGHTS_ENABLED=true

# FX Provider (Alpha Vantage)
FX_PROVIDER_TYPE=alpha-vantage
ALPHA_VANTAGE_API_KEY=demo
# Get free API key: https://www.alphavantage.co/support/#api-key
EOF

echo "✅ All resources created!"
echo ""
echo "📋 Connection details saved to: .env"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 Next Steps:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1. Start local Redis:"
echo "   docker run -d -p 6379:6379 redis:7-alpine"
echo ""
echo "2. Load environment variables:"
echo "   source .env"
echo ""
echo "3. Test services locally:"
echo "   cd rate-ingestion-service && mvn spring-boot:run"
echo ""
echo "4. View Azure resources:"
echo "   az resource list --resource-group $RESOURCE_GROUP --output table"
echo ""
echo "5. Clean up when done:"
echo "   az group delete --name $RESOURCE_GROUP --yes"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
```

**Make it executable and run:**
```bash
chmod +x setup-azure.sh
./setup-azure.sh
```

---

## 🧪 Verification Steps

### **1. Verify Resource Group**

```bash
az group show --name $RESOURCE_GROUP
```

### **2. Verify Cosmos DB**

```bash
# List databases
az cosmosdb sql database list \
  --account-name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP

# List containers
az cosmosdb sql container list \
  --account-name $COSMOS_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --database-name $COSMOS_DATABASE
```

### **3. Verify Event Hubs**

```bash
# List Event Hubs
az eventhubs eventhub list \
  --namespace-name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP

# List consumer groups
az eventhubs eventhub consumer-group list \
  --eventhub-name $EVENTHUB_NAME \
  --namespace-name $EVENTHUB_NAMESPACE \
  --resource-group $RESOURCE_GROUP
```

### **4. Verify Redis**

```bash
# Check Redis status
az redis show \
  --name $REDIS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "provisioningState"
```

### **5. List All Resources**

```bash
az resource list \
  --resource-group $RESOURCE_GROUP \
  --output table
```

---

## 🗑️ Cleanup (When Done)

**Delete everything:**
```bash
# Delete entire resource group (removes all resources)
az group delete \
  --name $RESOURCE_GROUP \
  --yes \
  --no-wait

echo "🗑️  Resources are being deleted..."
```

**Check deletion status:**
```bash
az group show --name $RESOURCE_GROUP
# Should show: "provisioningState": "Deleting"
```

---

## 📚 Next Steps After Deployment

After Azure infrastructure is deployed (via IaC or manual setup):

### **1. Get Alpha Vantage API Key** (30 seconds)

```bash
# Visit: https://www.alphavantage.co/support/#api-key
# Enter your email → Receive API key instantly (free!)

# Update .env file:
ALPHA_VANTAGE_API_KEY=your-key-here
FX_PROVIDER_TYPE=alpha-vantage
```

### **2. Start Local Redis** (if not using Azure Redis)

```bash
# Start Redis container
docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine

# Verify it's running
docker ps | grep redis
```

### **3. Load Environment Variables**

```bash
# Load the .env file (generated by deploy.sh or manually created)
source .env

# Verify variables are set
echo $COSMOS_ENDPOINT
echo $EVENTHUB_CONNECTION_STRING
```

### **4. Build and Test Services**

```bash
# Build common library
cd common-lib && mvn clean install && cd ..

# Run automated tests
./test-system.sh

# Start services (in separate terminals)
# Terminal 1:
cd rate-ingestion-service && mvn spring-boot:run

# Terminal 2:
cd fx-rates-api && mvn spring-boot:run

# Terminal 3:
cd websocket-service && mvn spring-boot:run
```

### **5. Verify End-to-End**

```bash
# Test REST API
curl http://localhost:8080/api/v1/rates/EUR/USD

# Test WebSocket
# Open test-websocket.html in browser
```

### **📖 Detailed Testing Guide**

For complete testing instructions: `LOCAL-TESTING-GUIDE.md`

### **🎯 For Interview Demo**

1. Practice deployment: `cd infrastructure && ./deploy.sh`
2. Practice cleanup: `./destroy.sh`
3. Time it - full deploy takes ~5-10 minutes
4. Know your talking points (see `infrastructure/README.md`)

### **💰 Cost Management**

```bash
# Stop costs when not using
cd infrastructure && ./destroy.sh

# Redeploy anytime
./deploy.sh
```

---

## 🆘 Troubleshooting

### **Issue: "az: command not found"**
```bash
# Install Azure CLI
winget install -e --id Microsoft.AzureCLI
```

### **Issue: "Subscription not found"**
```bash
# List subscriptions
az account list --output table

# Set correct subscription
az account set --subscription "YOUR_SUBSCRIPTION_NAME"
```

### **Issue: "Resource name already exists"**
```bash
# Names must be globally unique
# Add random suffix:
COSMOS_ACCOUNT_NAME="fexco-cosmos-$(openssl rand -hex 4)"
```

### **Issue: "Quota exceeded"**
```bash
# Check quotas
az vm list-usage --location $LOCATION --output table

# Request quota increase via Azure Portal
```

### **Issue: Redis provisioning takes too long**
```bash
# Use local Redis for development instead
docker run -d -p 6379:6379 redis:7-alpine
```

---

## 💡 Tips

1. **Use Serverless Cosmos DB** for dev/test (pay per request)
2. **Use local Redis** during development (free!)
3. **Enable free tier** for Application Insights
4. **Delete resources** when not in use
5. **Monitor costs** in Azure Portal
6. **Use Azure free credits** ($200 for first 30 days)

---

**Next:** [Local Testing Guide](LOCAL-TESTING-GUIDE.md)
