// ============================================================================
// Azure Cache for Redis Module (Optional)
// ============================================================================
// Note: For local development, use Docker instead:
//   docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine
// ============================================================================

@description('Redis cache name')
param cacheName string

@description('Location for resources')
param location string

@description('Resource tags')
param tags object

@description('Redis SKU')
@allowed([
  'Basic'
  'Standard'
  'Premium'
])
param sku string = 'Basic'

@description('Redis capacity (0-6 for Basic/Standard, 1-5 for Premium)')
@minValue(0)
@maxValue(6)
param capacity int = 0

// ============================================================================
// Azure Cache for Redis
// ============================================================================

resource redisCache 'Microsoft.Cache/redis@2023-08-01' = {
  name: cacheName
  location: location
  tags: tags
  properties: {
    sku: {
      name: sku
      family: sku == 'Premium' ? 'P' : 'C'
      capacity: capacity
    }

    // SSL enabled (required for production)
    enableNonSslPort: false
    minimumTlsVersion: '1.2'

    // Redis configuration
    redisConfiguration: {
      'maxmemory-policy': 'allkeys-lru'  // Evict least recently used keys
      'maxmemory-reserved': '30'          // Reserve memory for operations
    }

    // Zone redundancy (not available on Basic)
    // replicasPerMaster: sku == 'Premium' ? 1 : 0

    // Public network access
    publicNetworkAccess: 'Enabled'
  }
}

// ============================================================================
// Outputs
// ============================================================================

output cacheName string = redisCache.name
output hostName string = redisCache.properties.hostName
output sslPort int = redisCache.properties.sslPort
output port int = redisCache.properties.port

// Note: Redis keys must be retrieved via Azure CLI:
// az redis list-keys --name <cacheName> --resource-group <rgName>
