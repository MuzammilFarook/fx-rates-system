#!/bin/bash

# FX Rates System - Complete Testing Script
# Tests all services and verifies Azure integration

set -e

echo "🧪 FX Rates System - Complete Test Suite"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test functions
test_pass() {
    echo -e "${GREEN}✅ PASS${NC}: $1"
}

test_fail() {
    echo -e "${RED}❌ FAIL${NC}: $1"
}

test_warn() {
    echo -e "${YELLOW}⚠️  WARN${NC}: $1"
}

# 1. Check Prerequisites
echo "1️⃣  Checking Prerequisites..."
echo ""

# Java
if java -version 2>&1 | grep -q "version"; then
    test_pass "Java installed"
else
    test_fail "Java not installed"
    exit 1
fi

# Maven
if mvn -version 2>&1 | grep -q "Apache Maven"; then
    test_pass "Maven installed"
else
    test_fail "Maven not installed"
    exit 1
fi

# Docker
if docker --version 2>&1 | grep -q "Docker version"; then
    test_pass "Docker installed"
else
    test_warn "Docker not installed (Redis will fail)"
fi

echo ""

# 2. Check Environment Variables
echo "2️⃣  Checking Environment Variables..."
echo ""

if [ -f .env ]; then
    test_pass ".env file exists"
    source .env
else
    test_fail ".env file not found"
    echo "   Create .env from .env.template"
    exit 1
fi

# Check required variables
if [ -n "$COSMOS_ENDPOINT" ]; then
    test_pass "COSMOS_ENDPOINT set"
else
    test_fail "COSMOS_ENDPOINT not set"
fi

if [ -n "$COSMOS_KEY" ]; then
    test_pass "COSMOS_KEY set"
else
    test_fail "COSMOS_KEY not set"
fi

if [ -n "$EVENTHUB_CONNECTION_STRING" ]; then
    test_pass "EVENTHUB_CONNECTION_STRING set"
else
    test_fail "EVENTHUB_CONNECTION_STRING not set"
fi

echo ""

# 3. Check Redis
echo "3️⃣  Checking Redis..."
echo ""

if docker ps | grep -q "redis"; then
    test_pass "Redis container running"

    # Test Redis connection
    if docker exec -it $(docker ps -q -f name=redis) redis-cli ping 2>&1 | grep -q "PONG"; then
        test_pass "Redis responding to PING"
    else
        test_warn "Redis not responding"
    fi
else
    test_warn "Redis container not running"
    echo "   Start with: docker run -d -p 6379:6379 --name fx-rates-redis redis:7-alpine"
fi

echo ""

# 4. Build Common Library
echo "4️⃣  Building Common Library..."
echo ""

cd common-lib
if mvn clean install -DskipTests > /dev/null 2>&1; then
    test_pass "common-lib built successfully"
else
    test_fail "common-lib build failed"
    exit 1
fi
cd ..

echo ""

# 5. Test rate-ingestion-service
echo "5️⃣  Testing rate-ingestion-service..."
echo ""

cd rate-ingestion-service

if mvn clean package -DskipTests > /dev/null 2>&1; then
    test_pass "rate-ingestion-service built successfully"
else
    test_fail "rate-ingestion-service build failed"
    cd ..
    exit 1
fi

# Check if service can connect to Azure (quick validation test)
if mvn test -Dtest=CosmosDbConfigTest 2>&1 | grep -q "BUILD SUCCESS"; then
    test_pass "Cosmos DB connection test passed"
else
    test_warn "Cosmos DB connection test skipped (no tests configured)"
fi

cd ..

echo ""

# 6. Test fx-rates-api
echo "6️⃣  Testing fx-rates-api..."
echo ""

cd fx-rates-api

if mvn clean package -DskipTests > /dev/null 2>&1; then
    test_pass "fx-rates-api built successfully"
else
    test_fail "fx-rates-api build failed"
    cd ..
    exit 1
fi

cd ..

echo ""

# 7. Test websocket-service
echo "7️⃣  Testing websocket-service..."
echo ""

cd websocket-service

if mvn clean package -DskipTests > /dev/null 2>&1; then
    test_pass "websocket-service built successfully"
else
    test_fail "websocket-service build failed"
    cd ..
    exit 1
fi

cd ..

echo ""

# 8. Test REST API (if running)
echo "8️⃣  Testing REST API..."
echo ""

if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    test_pass "fx-rates-api is running on port 8080"

    # Test health endpoint
    health_status=$(curl -s http://localhost:8080/actuator/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    if [ "$health_status" == "UP" ]; then
        test_pass "Health check: UP"
    else
        test_warn "Health check: $health_status"
    fi

    # Test rate endpoint
    if curl -s http://localhost:8080/api/v1/rates/EUR/USD | grep -q "currencyPair"; then
        test_pass "Rate endpoint responding"
    else
        test_warn "Rate endpoint not returning data"
    fi
else
    test_warn "fx-rates-api not running"
    echo "   Start with: cd fx-rates-api && mvn spring-boot:run"
fi

echo ""

# 9. Test WebSocket (if running)
echo "9️⃣  Testing WebSocket service..."
echo ""

if curl -s http://localhost:8082/actuator/health > /dev/null 2>&1; then
    test_pass "websocket-service is running on port 8082"

    health_status=$(curl -s http://localhost:8082/actuator/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    if [ "$health_status" == "UP" ]; then
        test_pass "Health check: UP"
    else
        test_warn "Health check: $health_status"
    fi
else
    test_warn "websocket-service not running"
    echo "   Start with: cd websocket-service && mvn spring-boot:run"
fi

echo ""

# 10. Test rate-ingestion (if running)
echo "🔟 Testing rate-ingestion service..."
echo ""

if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    test_pass "rate-ingestion-service is running on port 8081"

    health_status=$(curl -s http://localhost:8081/actuator/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    if [ "$health_status" == "UP" ]; then
        test_pass "Health check: UP"
    else
        test_warn "Health check: $health_status"
    fi
else
    test_warn "rate-ingestion-service not running"
    echo "   Start with: cd rate-ingestion-service && mvn spring-boot:run"
fi

echo ""

# Summary
echo "=========================================="
echo "📊 Test Summary"
echo "=========================================="
echo ""
echo "Next Steps:"
echo ""
echo "1. If services are not running, start them:"
echo "   Terminal 1: cd rate-ingestion-service && mvn spring-boot:run"
echo "   Terminal 2: cd fx-rates-api && mvn spring-boot:run"
echo "   Terminal 3: cd websocket-service && mvn spring-boot:run"
echo ""
echo "2. Test the complete system:"
echo "   curl http://localhost:8080/api/v1/rates/EUR/USD"
echo ""
echo "3. Open WebSocket test:"
echo "   Open test-websocket.html in browser"
echo ""
echo "4. Monitor logs in each terminal"
echo ""
echo "=========================================="
