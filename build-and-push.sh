#!/bin/bash

# Build and Push Docker Images Script
# This script builds all microservices and pushes them to Azure Container Registry

set -e

ACR_NAME="fexcofxratesacr"
VERSION=${1:-latest}

echo "🏗️  Building and pushing FX Rates System microservices..."
echo "📦 Version: $VERSION"

# Login to ACR
echo "🔐 Logging into Azure Container Registry..."
az acr login --name $ACR_NAME

# Build and push fx-rates-api
echo ""
echo "📦 Building fx-rates-api..."
docker build -t $ACR_NAME.azurecr.io/fx-rates-api:$VERSION -f fx-rates-api/Dockerfile .
echo "⬆️  Pushing fx-rates-api..."
docker push $ACR_NAME.azurecr.io/fx-rates-api:$VERSION

# Build and push rate-ingestion-service
echo ""
echo "📦 Building rate-ingestion-service..."
docker build -t $ACR_NAME.azurecr.io/rate-ingestion-service:$VERSION -f rate-ingestion-service/Dockerfile .
echo "⬆️  Pushing rate-ingestion-service..."
docker push $ACR_NAME.azurecr.io/rate-ingestion-service:$VERSION

# Build and push websocket-service
echo ""
echo "📦 Building websocket-service..."
docker build -t $ACR_NAME.azurecr.io/websocket-service:$VERSION -f websocket-service/Dockerfile .
echo "⬆️  Pushing websocket-service..."
docker push $ACR_NAME.azurecr.io/websocket-service:$VERSION

echo ""
echo "✅ All images built and pushed successfully!"
echo ""
echo "📋 Pushed Images:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "• $ACR_NAME.azurecr.io/fx-rates-api:$VERSION"
echo "• $ACR_NAME.azurecr.io/rate-ingestion-service:$VERSION"
echo "• $ACR_NAME.azurecr.io/websocket-service:$VERSION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📝 Next: Deploy to Kubernetes with:"
echo "   kubectl apply -f k8s/base/"
