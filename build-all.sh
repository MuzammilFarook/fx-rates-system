#!/bin/bash

# Build script for independent microservices
# Each service is built separately (true microservices architecture)

set -e

echo "🏗️  Building FX Rates System - Independent Microservices"
echo "=========================================================="

# 1. Build common library first (install to local Maven repo)
echo ""
echo "📦 Step 1: Building common-lib..."
cd common-lib
mvn clean install -DskipTests
cd ..
echo "✅ common-lib installed to local Maven repository"

# 2. Build fx-rates-api
echo ""
echo "📦 Step 2: Building fx-rates-api..."
cd fx-rates-api
mvn clean package -DskipTests
cd ..
echo "✅ fx-rates-api built successfully"

# 3. Build rate-ingestion-service
echo ""
echo "📦 Step 3: Building rate-ingestion-service..."
cd rate-ingestion-service
mvn clean package -DskipTests
cd ..
echo "✅ rate-ingestion-service built successfully"

# 4. Build websocket-service
echo ""
echo "📦 Step 4: Building websocket-service..."
cd websocket-service
mvn clean package -DskipTests
cd ..
echo "✅ websocket-service built successfully"

echo ""
echo "=========================================================="
echo "✅ All microservices built successfully!"
echo ""
echo "📦 Build artifacts:"
echo "  • common-lib/target/fx-rates-common-lib-1.0.0-SNAPSHOT.jar"
echo "  • fx-rates-api/target/fx-rates-api-1.0.0-SNAPSHOT.jar"
echo "  • rate-ingestion-service/target/rate-ingestion-service-1.0.0-SNAPSHOT.jar"
echo "  • websocket-service/target/websocket-service-1.0.0-SNAPSHOT.jar"
echo ""
echo "🚀 Next steps:"
echo "  • Run locally: cd <service-name> && mvn spring-boot:run"
echo "  • Build Docker images: ./build-docker-images.sh"
echo "  • Deploy to Kubernetes: kubectl apply -f k8s/base/"
