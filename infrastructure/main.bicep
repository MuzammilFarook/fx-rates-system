// ============================================================================
// FX Rates System - Azure Infrastructure as Code (Bicep)
// ============================================================================
// This template creates all required Azure resources for the FX Rates System
//
// Resources Created:
// - Cosmos DB (Serverless)
// - Event Hubs Namespace (Basic tier)
// - Event Hub with consumer groups
// - Optional: Azure Cache for Redis (can use local Docker instead)
//
// Usage:
//   az deployment sub create --location westus2 --template-file main.bicep
//   az group delete --name fexco-fx-rates-rg --yes  // cleanup
// ============================================================================

targetScope = 'subscription'

// ============================================================================
// Parameters
// ============================================================================

@description('Resource group name')
param resourceGroupName string = 'fexco-fx-rates-rg'

@description('Azure region for resources')
param location string = 'westus2'

@description('Environment (dev, test, prod)')
@allowed([
  'dev'
  'test'
  'prod'
])
param environment string = 'dev'

@description('Cosmos DB account name (must be globally unique)')
param cosmosAccountName string = 'fexco-cosmos-${uniqueString(subscription().subscriptionId)}'

@description('Cosmos DB database name')
param cosmosDatabaseName string = 'fxrates'

@description('Cosmos DB container name')
param cosmosContainerName string = 'rates'

@description('Event Hubs namespace name (must be globally unique)')
param eventHubNamespaceName string = 'fexco-eventhub-${uniqueString(subscription().subscriptionId)}'

@description('Event Hub name')
param eventHubName string = 'fx-rates-updates'

@description('Event Hub SKU tier (Basic or Standard)')
@allowed([
  'Basic'
  'Standard'
])
param eventHubSkuTier string = 'Standard'

@description('Event Hub consumer group for websocket service')
param eventHubConsumerGroup string = 'websocket-service'

@description('Deploy Azure Cache for Redis (true) or use local Docker (false)')
param deployRedis bool = false

@description('Redis cache name (if deploying)')
param redisCacheName string = 'fexco-redis-${uniqueString(subscription().subscriptionId)}'

@description('Tags to apply to all resources')
param tags object = {
  Project: 'FX-Rates-System'
  Environment: environment
  ManagedBy: 'Bicep'
  Purpose: 'Interview-Demo'
}

// ============================================================================
// Resource Group
// ============================================================================

resource rg 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: resourceGroupName
  location: location
  tags: tags
}

// ============================================================================
// Cosmos DB (Serverless - Cost Optimized)
// ============================================================================

module cosmosDb 'modules/cosmos-db.bicep' = {
  scope: rg
  name: 'cosmosDbDeployment'
  params: {
    accountName: cosmosAccountName
    location: location
    databaseName: cosmosDatabaseName
    containerName: cosmosContainerName
    tags: tags
  }
}

// ============================================================================
// Event Hubs
// ============================================================================

module eventHub 'modules/event-hub.bicep' = {
  scope: rg
  name: 'eventHubDeployment'
  params: {
    namespaceName: eventHubNamespaceName
    eventHubName: eventHubName
    consumerGroupName: eventHubConsumerGroup
    skuTier: eventHubSkuTier
    location: location
    tags: tags
  }
}

// ============================================================================
// Redis Cache (Optional)
// ============================================================================

module redis 'modules/redis.bicep' = if (deployRedis) {
  scope: rg
  name: 'redisDeployment'
  params: {
    cacheName: redisCacheName
    location: location
    tags: tags
  }
}

// ============================================================================
// Outputs
// ============================================================================

output resourceGroupName string = rg.name
output location string = location

// Cosmos DB Outputs
output cosmosEndpoint string = cosmosDb.outputs.endpoint
output cosmosDatabaseName string = cosmosDatabaseName
output cosmosContainerName string = cosmosContainerName

// Event Hub Outputs
output eventHubNamespace string = eventHub.outputs.namespaceName
output eventHubName string = eventHubName
output eventHubConsumerGroup string = eventHub.outputs.consumerGroupName

// Redis Outputs (if deployed)
output redisHostName string = deployRedis ? redis.outputs.hostName : 'localhost'
output redisPort int = deployRedis ? redis.outputs.sslPort : 6379
output redisSslEnabled bool = deployRedis

// Instructions
output nextSteps string = '''
✅ Azure infrastructure deployed successfully!

📋 Next Steps:

1. Get Cosmos DB Key:
   az cosmosdb keys list --name ${cosmosAccountName} --resource-group ${resourceGroupName} --query "primaryMasterKey" -o tsv

2. Get Event Hub Connection String:
   az eventhubs namespace authorization-rule keys list --namespace-name ${eventHubNamespaceName} --resource-group ${resourceGroupName} --name RootManageSharedAccessKey --query "primaryConnectionString" -o tsv

3. Get Redis Key (if deployed):
   az redis list-keys --name ${redisCacheName} --resource-group ${resourceGroupName} --query "primaryKey" -o tsv

4. Run the helper script to generate .env file:
   cd infrastructure && ./get-connection-strings.sh

5. Start local testing:
   Follow LOCAL-TESTING-GUIDE.md

💰 Cost: ~$12-15/month with serverless Cosmos DB
🗑️  Cleanup: az group delete --name ${resourceGroupName} --yes --no-wait
'''
