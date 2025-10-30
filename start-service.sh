#!/bin/bash

# ============================================================================
# Start Single Service with Environment Variables
# ============================================================================
# Usage: ./start-service.sh <service-name>
# Example: ./start-service.sh rate-ingestion-service
# ============================================================================

if [ -z "$1" ]; then
    echo "Usage: ./start-service.sh <service-name>"
    echo ""
    echo "Available services:"
    echo "  - rate-ingestion-service"
    echo "  - fx-rates-api"
    echo "  - websocket-service"
    exit 1
fi

SERVICE=$1

# Check if .env file exists
if [ ! -f .env ]; then
    echo "❌ .env file not found!"
    echo "Run: cd infrastructure && ./get-connection-strings.sh"
    exit 1
fi

# Load environment variables
echo "📋 Loading environment variables from .env..."
set -a
source .env
set +a

# Verify critical variables
if [ -z "$COSMOS_ENDPOINT" ]; then
    echo "⚠️  Warning: COSMOS_ENDPOINT not set"
fi

if [ -z "$EVENTHUB_CONNECTION_STRING" ]; then
    echo "⚠️  Warning: EVENTHUB_CONNECTION_STRING not set"
fi

echo "✅ Environment variables loaded"
echo ""

# Check if service directory exists
if [ ! -d "$SERVICE" ]; then
    echo "❌ Service directory not found: $SERVICE"
    exit 1
fi

# Start the service
echo "🚀 Starting $SERVICE..."
echo ""
cd "$SERVICE"
mvn spring-boot:run
