#!/bin/bash

# ============================================================================
# Get Connection Strings - Helper Script
# ============================================================================
# This script retrieves connection strings from already-deployed resources
# Useful if you need to regenerate .env file
# ============================================================================

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

print_success() { echo -e "${GREEN}✅ $1${NC}"; }
print_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }

# ============================================================================
# Check Prerequisites
# ============================================================================

if ! az account show &> /dev/null; then
    print_error "Not logged in to Azure!"
    echo "Run: az login"
    exit 1
fi

# ============================================================================
# Find Resource Group
# ============================================================================

RESOURCE_GROUP="fexco-fx-rates-rg"

if ! az group exists --name "$RESOURCE_GROUP" | grep -q "true"; then
    print_error "Resource group '$RESOURCE_GROUP' not found"

    # Try to find it
    RG_LIST=$(az group list --tag Project=FX-Rates-System --query "[].name" -o tsv)

    if [ -z "$RG_LIST" ]; then
        print_error "No FX Rates System resources found"
        echo "Deploy first with: ./deploy.sh"
        exit 1
    fi

    echo "Found these resource groups:"
    echo "$RG_LIST"
    read -p "Enter resource group name: " RESOURCE_GROUP
fi

print_success "Found resource group: $RESOURCE_GROUP"

echo ""
print_info "Retrieving connection strings..."
echo ""

# ============================================================================
# Get Connection Strings
# ============================================================================

# Cosmos DB
print_info "Fetching Cosmos DB..."
COSMOS_ACCOUNT=$(az cosmosdb list --resource-group "$RESOURCE_GROUP" --query "[0].name" -o tsv)
COSMOS_ENDPOINT=$(az cosmosdb show --name "$COSMOS_ACCOUNT" --resource-group "$RESOURCE_GROUP" --query "documentEndpoint" -o tsv)
COSMOS_KEY=$(az cosmosdb keys list --name "$COSMOS_ACCOUNT" --resource-group "$RESOURCE_GROUP" --query "primaryMasterKey" -o tsv)
print_success "Cosmos DB"

# Event Hubs
print_info "Fetching Event Hub..."
EVENTHUB_NAMESPACE=$(az eventhubs namespace list --resource-group "$RESOURCE_GROUP" --query "[0].name" -o tsv)
EVENTHUB_CONNECTION_STRING=$(az eventhubs namespace authorization-rule keys list \
    --namespace-name "$EVENTHUB_NAMESPACE" \
    --resource-group "$RESOURCE_GROUP" \
    --name RootManageSharedAccessKey \
    --query "primaryConnectionString" -o tsv)
print_success "Event Hub"

# Redis (optional)
REDIS_CACHE=$(az redis list --resource-group "$RESOURCE_GROUP" --query "[0].name" -o tsv 2>/dev/null || echo "")

if [ -n "$REDIS_CACHE" ]; then
    print_info "Fetching Redis..."
    REDIS_HOST=$(az redis show --name "$REDIS_CACHE" --resource-group "$RESOURCE_GROUP" --query "hostName" -o tsv)
    REDIS_KEY=$(az redis list-keys --name "$REDIS_CACHE" --resource-group "$RESOURCE_GROUP" --query "primaryKey" -o tsv)
    REDIS_PORT=6380
    REDIS_SSL_ENABLED=true
    print_success "Redis Cache"
else
    REDIS_HOST="localhost"
    REDIS_PORT=6379
    REDIS_KEY=""
    REDIS_SSL_ENABLED=false
    print_info "Redis: Using local Docker"
fi

echo ""

# ============================================================================
# Generate .env File
# ============================================================================

ENV_FILE="../.env"

cat > "$ENV_FILE" << EOF
# ===================================
# Azure FX Rates System - Environment Variables
# Retrieved: $(date)
# ===================================

# ===================================
# Azure Cosmos DB
# ===================================
COSMOS_ENDPOINT=$COSMOS_ENDPOINT
COSMOS_KEY=$COSMOS_KEY
COSMOS_DATABASE=fxrates

# ===================================
# Azure Event Hubs
# ===================================
EVENTHUB_CONNECTION_STRING=$EVENTHUB_CONNECTION_STRING
EVENTHUB_NAMESPACE=$EVENTHUB_NAMESPACE
EVENTHUB_TOPIC=fx-rates-updates
EVENTHUB_CONSUMER_GROUP=websocket-service

# ===================================
# Redis Cache
# ===================================
REDIS_HOST=$REDIS_HOST
REDIS_PORT=$REDIS_PORT
REDIS_PASSWORD=$REDIS_KEY
REDIS_SSL_ENABLED=$REDIS_SSL_ENABLED

# ===================================
# FX Rate Provider Configuration
# ===================================
FX_PROVIDER_TYPE=demo
ALPHA_VANTAGE_API_KEY=demo
EOF

print_success ".env file created at: $ENV_FILE"

echo ""
echo "📋 Next Steps:"
echo "1. Get Alpha Vantage API key: https://www.alphavantage.co/support/#api-key"
echo "2. Update ALPHA_VANTAGE_API_KEY in .env"
echo "3. Update FX_PROVIDER_TYPE to 'alpha-vantage'"
if [ "$REDIS_HOST" = "localhost" ]; then
echo "4. Start local Redis: docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine"
fi
echo ""
