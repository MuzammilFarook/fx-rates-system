# FX Rates System - Infrastructure as Code (Bicep)

This directory contains Azure Infrastructure as Code (IaC) templates using **Bicep** for the FX Rates System.

## 🎯 What This Does

Creates and manages all Azure resources with **single commands**:

```bash
# Create everything
./deploy.sh

# Destroy everything (stop costs)
./destroy.sh
```

## 📁 Directory Structure

```
infrastructure/
├── main.bicep                      # Main template (orchestrator)
├── parameters.json                 # Configuration parameters
├── modules/
│   ├── cosmos-db.bicep            # Cosmos DB (serverless)
│   ├── event-hub.bicep            # Event Hubs (Basic tier)
│   └── redis.bicep                # Azure Cache for Redis (optional)
├── deploy.sh                      # Deployment script
├── destroy.sh                     # Cleanup script
├── get-connection-strings.sh      # Retrieve existing credentials
└── README.md                      # This file
```

## 🚀 Quick Start

### **Prerequisites**

1. **Azure CLI** - Install from: https://aka.ms/installazurecliwindows
2. **Azure Account** - Free tier works!
3. **Login to Azure**:
   ```bash
   az login
   ```

### **Deploy Infrastructure (5-10 minutes)**

```bash
cd infrastructure

# Deploy without Azure Redis (use local Docker - recommended)
./deploy.sh

# OR deploy with Azure Redis (+$15-20/month)
./deploy.sh --with-redis
```

**What Happens:**
- ✅ Creates resource group
- ✅ Creates Cosmos DB (serverless)
- ✅ Creates Event Hubs (Basic tier)
- ✅ Retrieves all connection strings
- ✅ Generates `.env` file automatically
- ⏱️ Takes ~5-10 minutes

### **Destroy Infrastructure (Stop Costs)**

```bash
cd infrastructure

# Interactive confirmation
./destroy.sh

# Skip confirmation (careful!)
./destroy.sh --yes
```

**What Happens:**
- 🗑️ Deletes resource group and ALL resources
- 💰 Stops all Azure charges
- 🔄 Can redeploy anytime with `./deploy.sh`
- ⏱️ Takes ~3-5 minutes (runs in background)

## 💰 Cost Breakdown

| Resource | SKU/Tier | Monthly Cost | Notes |
|----------|----------|--------------|-------|
| **Cosmos DB** | Serverless | ~$1-2 | Pay per request, no fixed cost |
| **Event Hubs** | Basic | ~$11 | 1 throughput unit |
| **Redis** | Local Docker | **FREE** | Recommended for dev/demo |
| **Redis** | Azure Basic C0 | ~$17 | Optional (use --with-redis) |
| **Total** | | **~$12-15/month** | With local Redis |

**Free Tier Benefits:**
- $200 Azure credits for 30 days (new accounts)
- Cosmos DB: First 1000 RU/s free monthly
- Event Hubs: First 1M events free monthly

**Interview Demo:** The system will cost less than $1 for a 1-hour demo!

## 🏗️ What Gets Created

### **1. Resource Group**
```
Name: fexco-fx-rates-rg
Location: East US
Tags: Project=FX-Rates-System, Environment=dev
```

### **2. Cosmos DB (Serverless)**
```
Account: fexco-cosmos-XXXXXXXX (globally unique)
Database: fxrates
Container: rates
Partition Key: /currencyPair
Consistency: Session
Backup: Periodic (4 hours)
```

### **3. Event Hubs**
```
Namespace: fexco-eventhub-XXXXXXXX (globally unique)
Event Hub: fx-rates-updates
Partitions: 2
Retention: 1 day
Consumer Group: websocket-service
```

### **4. Redis (Optional)**
```
Cache Name: fexco-redis-XXXXXXXX
SKU: Basic C0 (250 MB)
TLS: Enabled
Port: 6380 (SSL)
```

**OR Local Redis (Recommended):**
```bash
docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine
```

## 📝 Configuration

### **Edit Parameters**

Customize deployment by editing `parameters.json`:

```json
{
  "parameters": {
    "resourceGroupName": {
      "value": "fexco-fx-rates-rg"  // Change resource group name
    },
    "location": {
      "value": "eastus"  // Change region
    },
    "environment": {
      "value": "dev"  // dev | test | prod
    },
    "deployRedis": {
      "value": false  // true to deploy Azure Redis
    }
  }
}
```

### **Supported Locations**

```bash
# List available regions
az account list-locations --query "[].name" -o table

# Recommended regions (good for demos)
eastus, westus2, westeurope, northeurope, uksouth
```

## 🔧 Advanced Usage

### **Deploy to Different Environment**

```bash
# Deploy to test environment
./deploy.sh

# Then manually change tags
az group update --name fexco-fx-rates-rg --set tags.Environment=test
```

### **Deploy Specific Modules**

```bash
# Deploy only Cosmos DB
az deployment group create \
  --resource-group fexco-fx-rates-rg \
  --template-file modules/cosmos-db.bicep \
  --parameters accountName=my-cosmos location=eastus

# Deploy only Event Hub
az deployment group create \
  --resource-group fexco-fx-rates-rg \
  --template-file modules/event-hub.bicep \
  --parameters namespaceName=my-eventhub location=eastus
```

### **Retrieve Connection Strings Anytime**

If you need to regenerate the `.env` file:

```bash
cd infrastructure
./get-connection-strings.sh
```

This retrieves credentials from existing resources without redeploying.

### **Validate Template**

Before deploying, validate the Bicep template:

```bash
# Validate syntax
az bicep build --file main.bicep

# Validate deployment
az deployment sub validate \
  --location eastus \
  --template-file main.bicep \
  --parameters @parameters.json
```

### **Preview Changes (What-If)**

See what would change before deploying:

```bash
az deployment sub what-if \
  --location eastus \
  --template-file main.bicep \
  --parameters @parameters.json
```

## 🎓 For Your Interview

### **Key Talking Points**

1. **Infrastructure as Code**
   > "I used Bicep for IaC, which allows repeatable, version-controlled infrastructure deployments. I can spin up the entire environment in 5 minutes and tear it down just as quickly to control costs."

2. **Cost Optimization**
   > "I chose serverless Cosmos DB which eliminates fixed costs—we only pay for actual usage. For a demo, this costs less than $1. The entire stack is under $15/month in dev."

3. **Modularity**
   > "The infrastructure is modular—each resource is in its own Bicep module. This makes it easy to swap components, like upgrading from Basic to Standard Event Hubs without touching other resources."

4. **Production Ready**
   > "This same infrastructure can scale to production by adjusting parameters: change environment=prod, enable zone redundancy, add Application Insights. No code changes needed."

### **Demo Flow**

```bash
# 1. Show the code
cat infrastructure/main.bicep

# 2. Deploy (time it!)
time ./deploy.sh

# 3. Show resources in portal
az resource list --resource-group fexco-fx-rates-rg --output table

# 4. Run the application (see LOCAL-TESTING-GUIDE.md)

# 5. Clean up
./destroy.sh --yes
```

## 🔍 Monitoring & Troubleshooting

### **Check Deployment Status**

```bash
# List recent deployments
az deployment sub list --output table

# Show deployment details
az deployment sub show --name <deployment-name>

# View deployment errors
az deployment sub show --name <deployment-name> --query "properties.error"
```

### **Verify Resources**

```bash
# List all resources
az resource list --resource-group fexco-fx-rates-rg --output table

# Check specific resource
az cosmosdb show --name <cosmos-name> --resource-group fexco-fx-rates-rg
az eventhubs namespace show --name <eventhub-name> --resource-group fexco-fx-rates-rg
```

### **Common Issues**

**1. Name Already Exists**
```
Error: The resource name is already taken
Solution: Cosmos/EventHub names are globally unique.
          The template uses uniqueString() to generate unique names.
          If still conflicts, edit parameters.json with custom names.
```

**2. Quota Exceeded**
```
Error: Quota exceeded for Cosmos DB accounts
Solution: Free tier allows 1 serverless account per subscription.
          Delete existing accounts or use different subscription.
```

**3. Permission Denied**
```
Error: Authorization failed
Solution: Ensure you have Contributor or Owner role on subscription.
          Check: az role assignment list --assignee <your-email>
```

**4. Region Not Available**
```
Error: Resource type not available in region
Solution: Change location in parameters.json to supported region.
          Check: az provider show --namespace Microsoft.DocumentDB
```

## 📚 Learn More

- **Bicep Documentation**: https://learn.microsoft.com/azure/azure-resource-manager/bicep/
- **Cosmos DB Pricing**: https://azure.microsoft.com/pricing/details/cosmos-db/
- **Event Hubs Pricing**: https://azure.microsoft.com/pricing/details/event-hubs/
- **Azure Free Tier**: https://azure.microsoft.com/free/

## 🎯 Next Steps

After deploying infrastructure:

1. **Get Alpha Vantage API Key** (30 seconds)
   - Visit: https://www.alphavantage.co/support/#api-key
   - Update `.env` file with your key

2. **Start Local Redis** (if not using Azure Redis)
   ```bash
   docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine
   ```

3. **Follow Testing Guide**
   ```bash
   # Go back to project root
   cd ..

   # Follow the guide
   cat LOCAL-TESTING-GUIDE.md
   ```

4. **Run the System**
   ```bash
   # Load environment variables
   source .env

   # Build common library
   cd common-lib && mvn clean install && cd ..

   # Start services (separate terminals)
   cd rate-ingestion-service && mvn spring-boot:run
   cd fx-rates-api && mvn spring-boot:run
   cd websocket-service && mvn spring-boot:run
   ```

## ✅ Success Checklist

- [ ] Azure CLI installed and logged in
- [ ] Infrastructure deployed successfully
- [ ] `.env` file generated with connection strings
- [ ] Alpha Vantage API key obtained
- [ ] Local Redis running (or Azure Redis deployed)
- [ ] All 3 services built and running
- [ ] System tested end-to-end
- [ ] Ready for interview demo!

---

**Need Help?**
- Check `../AZURE-SETUP-GUIDE.md` for detailed Azure setup
- Check `../LOCAL-TESTING-GUIDE.md` for testing instructions
- Check `../rate-ingestion-service/PROVIDER-IMPLEMENTATION.md` for provider details

**Ready to Go?**
```bash
./deploy.sh
```
