#!/bin/bash

# ============================================================================
# FX Rates System - Azure Infrastructure Deployment Script
# ============================================================================
# This script deploys all Azure resources using Bicep IaC
#
# Prerequisites:
#   - Azure CLI installed (az --version)
#   - Logged in to Azure (az login)
#   - Active subscription
#
# Usage:
#   ./deploy.sh [--with-redis]
#
# Options:
#   --with-redis    Deploy Azure Cache for Redis (default: use local Docker)
# ============================================================================

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}======================================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}======================================================================${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# ============================================================================
# Parse Arguments
# ============================================================================

DEPLOY_REDIS=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --with-redis)
            DEPLOY_REDIS=true
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Usage: ./deploy.sh [--with-redis]"
            exit 1
            ;;
    esac
done

# ============================================================================
# Prerequisites Check
# ============================================================================

print_header "🔍 Checking Prerequisites"

# Check Azure CLI
if ! command -v az &> /dev/null; then
    print_error "Azure CLI not found!"
    echo "Install from: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli"
    exit 1
fi
print_success "Azure CLI installed"

# Check if logged in
if ! az account show &> /dev/null; then
    print_error "Not logged in to Azure!"
    echo "Run: az login"
    exit 1
fi
print_success "Logged in to Azure"

# Show subscription
SUBSCRIPTION_NAME=$(az account show --query "name" -o tsv)
SUBSCRIPTION_ID=$(az account show --query "id" -o tsv)
print_info "Using subscription: $SUBSCRIPTION_NAME ($SUBSCRIPTION_ID)"

echo ""

# ============================================================================
# Deployment
# ============================================================================

print_header "🚀 Deploying Azure Infrastructure"

LOCATION="westus2"
DEPLOYMENT_NAME="fx-rates-infra-$(date +%Y%m%d-%H%M%S)"

print_info "Deployment name: $DEPLOYMENT_NAME"
print_info "Location: $LOCATION"
print_info "Deploy Redis: $DEPLOY_REDIS"

echo ""
print_info "Starting deployment (this may take 5-10 minutes)..."
echo ""

# Build parameters
PARAMETERS="parameters.json"

# Override deployRedis if flag is set
if [ "$DEPLOY_REDIS" = true ]; then
    print_warning "Deploying Azure Cache for Redis will increase monthly cost by ~$15-20"
fi

# Deploy
az deployment sub create \
    --name "$DEPLOYMENT_NAME" \
    --location "$LOCATION" \
    --template-file main.bicep \
    --parameters @"$PARAMETERS" \
    --parameters deployRedis=$DEPLOY_REDIS \
    --output table

print_success "Deployment completed!"

echo ""

# ============================================================================
# Get Resource Group Name
# ============================================================================

RESOURCE_GROUP=$(az deployment sub show --name "$DEPLOYMENT_NAME" --query "properties.outputs.resourceGroupName.value" -o tsv)

print_info "Resource Group: $RESOURCE_GROUP"

echo ""

# ============================================================================
# Retrieve Connection Strings
# ============================================================================

print_header "🔑 Retrieving Connection Strings"

echo ""
print_info "Fetching Cosmos DB credentials..."

# Cosmos DB
COSMOS_ACCOUNT=$(az cosmosdb list --resource-group "$RESOURCE_GROUP" --query "[0].name" -o tsv)
COSMOS_ENDPOINT=$(az cosmosdb show --name "$COSMOS_ACCOUNT" --resource-group "$RESOURCE_GROUP" --query "documentEndpoint" -o tsv)
COSMOS_KEY=$(az cosmosdb keys list --name "$COSMOS_ACCOUNT" --resource-group "$RESOURCE_GROUP" --query "primaryMasterKey" -o tsv)

print_success "Cosmos DB credentials retrieved"

echo ""
print_info "Fetching Event Hub credentials..."

# Event Hubs
EVENTHUB_NAMESPACE=$(az eventhubs namespace list --resource-group "$RESOURCE_GROUP" --query "[0].name" -o tsv)
EVENTHUB_CONNECTION_STRING=$(az eventhubs namespace authorization-rule keys list \
    --namespace-name "$EVENTHUB_NAMESPACE" \
    --resource-group "$RESOURCE_GROUP" \
    --name RootManageSharedAccessKey \
    --query "primaryConnectionString" -o tsv)

print_success "Event Hub credentials retrieved"

# Redis (if deployed)
if [ "$DEPLOY_REDIS" = true ]; then
    echo ""
    print_info "Fetching Redis credentials..."

    REDIS_CACHE=$(az redis list --resource-group "$RESOURCE_GROUP" --query "[0].name" -o tsv)
    REDIS_HOST=$(az redis show --name "$REDIS_CACHE" --resource-group "$RESOURCE_GROUP" --query "hostName" -o tsv)
    REDIS_KEY=$(az redis list-keys --name "$REDIS_CACHE" --resource-group "$RESOURCE_GROUP" --query "primaryKey" -o tsv)
    REDIS_PORT=6380
    REDIS_SSL_ENABLED=true

    print_success "Redis credentials retrieved"
else
    REDIS_HOST="localhost"
    REDIS_PORT=6379
    REDIS_KEY=""
    REDIS_SSL_ENABLED=false
fi

echo ""

# ============================================================================
# Generate .env File
# ============================================================================

print_header "📝 Generating .env File"

ENV_FILE="../.env"

cat > "$ENV_FILE" << EOF
# ===================================
# Azure FX Rates System - Environment Variables
# Generated: $(date)
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
# Provider type: alpha-vantage | mock-reuters | demo
FX_PROVIDER_TYPE=demo

# Alpha Vantage (Professional provider with real bid/ask)
# Get free API key: https://www.alphavantage.co/support/#api-key
ALPHA_VANTAGE_API_KEY=demo

# ===================================
# Notes
# ===================================
# 1. Get Alpha Vantage API key: https://www.alphavantage.co/support/#api-key
# 2. Change FX_PROVIDER_TYPE to 'alpha-vantage' for production data
# 3. Use 'mock-reuters' for offline demos
$(if [ "$DEPLOY_REDIS" = false ]; then echo "# 4. Start local Redis: docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine"; fi)
EOF

print_success ".env file created at: $ENV_FILE"

echo ""

# ============================================================================
# Summary
# ============================================================================

print_header "✅ Deployment Complete!"

echo ""
echo "📊 Resource Summary:"
echo "  • Resource Group:    $RESOURCE_GROUP"
echo "  • Cosmos DB:         $COSMOS_ACCOUNT"
echo "  • Event Hub:         $EVENTHUB_NAMESPACE"
if [ "$DEPLOY_REDIS" = true ]; then
echo "  • Redis Cache:       $REDIS_CACHE"
else
echo "  • Redis:             Local Docker (use docker run command)"
fi

echo ""
echo "📋 Next Steps:"
echo ""
echo "1️⃣  Get Alpha Vantage API Key (30 seconds):"
echo "   https://www.alphavantage.co/support/#api-key"
echo ""

if [ "$DEPLOY_REDIS" = false ]; then
echo "2️⃣  Start Local Redis:"
echo "   docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine"
echo ""
fi

echo "3️⃣  Load Environment Variables:"
echo "   source .env"
echo ""

echo "4️⃣  Build and Run Services:"
echo "   Follow ../LOCAL-TESTING-GUIDE.md"
echo ""

echo "💰 Estimated Monthly Cost: \$12-15 (serverless Cosmos DB)"
echo ""

echo "🗑️  To Delete Everything:"
echo "   cd infrastructure && ./destroy.sh"
echo ""

print_header "🎉 Ready to Go!"
