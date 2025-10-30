// ============================================================================
// Event Hubs Module
// ============================================================================

@description('Event Hubs namespace name')
param namespaceName string

@description('Event Hub name')
param eventHubName string

@description('Consumer group name (only used for Standard/Premium tier)')
param consumerGroupName string

@description('Location for resources')
param location string

@description('Resource tags')
param tags object

@description('Event Hub SKU tier')
@allowed([
  'Basic'
  'Standard'
])
param skuTier string = 'Standard'

// ============================================================================
// Event Hubs Namespace
// ============================================================================

resource eventHubNamespace 'Microsoft.EventHub/namespaces@2023-01-01-preview' = {
  name: namespaceName
  location: location
  tags: tags
  sku: {
    name: skuTier
    tier: skuTier
    capacity: 1
  }
  properties: {
    minimumTlsVersion: '1.2'
    isAutoInflateEnabled: false
    zoneRedundant: false
  }
}

// ============================================================================
// Event Hub
// ============================================================================

resource eventHub 'Microsoft.EventHub/namespaces/eventhubs@2023-01-01-preview' = {
  parent: eventHubNamespace
  name: eventHubName
  properties: {
    messageRetentionInDays: 1  // Basic tier: 1 day retention
    partitionCount: 2          // 2 partitions for parallel processing
    status: 'Active'
  }
}

// ============================================================================
// Consumer Group (websocket-service)
// Note: Basic tier only supports $Default consumer group
// Custom consumer groups require Standard or Premium tier
// ============================================================================

resource consumerGroup 'Microsoft.EventHub/namespaces/eventhubs/consumergroups@2023-01-01-preview' = if (skuTier != 'Basic') {
  parent: eventHub
  name: consumerGroupName
  properties: {
    userMetadata: 'Consumer group for websocket-service to broadcast real-time updates'
  }
}

// ============================================================================
// Authorization Rule (RootManageSharedAccessKey - auto-created)
// ============================================================================

// Note: RootManageSharedAccessKey is automatically created by Azure
// We reference it for connection string output

// ============================================================================
// Outputs
// ============================================================================

output namespaceName string = eventHubNamespace.name
output eventHubName string = eventHub.name
output consumerGroupName string = skuTier == 'Basic' ? '$Default' : consumerGroup.name

// Connection string will be retrieved via Azure CLI after deployment
output namespaceId string = eventHubNamespace.id
