// ============================================================================
// Cosmos DB Module - Serverless Configuration
// ============================================================================

@description('Cosmos DB account name')
param accountName string

@description('Location for resources')
param location string

@description('Database name')
param databaseName string

@description('Container name')
param containerName string

@description('Resource tags')
param tags object

// ============================================================================
// Cosmos DB Account (Serverless)
// ============================================================================

resource cosmosAccount 'Microsoft.DocumentDB/databaseAccounts@2023-04-15' = {
  name: accountName
  location: location
  tags: tags
  kind: 'GlobalDocumentDB'
  properties: {
    databaseAccountOfferType: 'Standard'

    // Serverless capability (no RU provisioning, pay per request)
    capabilities: [
      {
        name: 'EnableServerless'
      }
    ]

    // Consistency level
    consistencyPolicy: {
      defaultConsistencyLevel: 'Session'
    }

    // Single region (cost optimization)
    locations: [
      {
        locationName: location
        failoverPriority: 0
        isZoneRedundant: false
      }
    ]

    // Enable automatic failover
    enableAutomaticFailover: false
    enableMultipleWriteLocations: false

    // Backup policy
    backupPolicy: {
      type: 'Periodic'
      periodicModeProperties: {
        backupIntervalInMinutes: 240
        backupRetentionIntervalInHours: 8
      }
    }
  }
}

// ============================================================================
// Database
// ============================================================================

resource database 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases@2023-04-15' = {
  parent: cosmosAccount
  name: databaseName
  properties: {
    resource: {
      id: databaseName
    }
  }
}

// ============================================================================
// Container with Partition Key
// ============================================================================

resource container 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers@2023-04-15' = {
  parent: database
  name: containerName
  properties: {
    resource: {
      id: containerName

      // Partition key strategy
      partitionKey: {
        paths: [
          '/currencyPair'
        ]
        kind: 'Hash'
      }

      // Indexing policy (optimized for queries)
      indexingPolicy: {
        indexingMode: 'consistent'
        automatic: true
        includedPaths: [
          {
            path: '/*'
          }
        ]
        excludedPaths: [
          {
            path: '/"_etag"/?'
          }
        ]
      }

      // Default TTL (none - we manage TTL via application)
      defaultTtl: -1
    }
  }
}

// ============================================================================
// Outputs
// ============================================================================

output accountName string = cosmosAccount.name
output endpoint string = cosmosAccount.properties.documentEndpoint
output databaseName string = database.name
output containerName string = container.name
