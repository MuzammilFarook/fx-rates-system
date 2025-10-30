# Quick Start Guide

## 📖 First Time After Reopening IDE?

**Read this first:** `PROJECT-CONTEXT.md` (complete conversation summary)

---

## ⚡ Quick Commands

### Build All Services
```bash
cd fx-rates-system
./build-all.sh
```

### Run Locally
```bash
# Option 1: Docker Compose (easiest)
docker-compose up --build

# Option 2: Run each service
cd fx-rates-api && mvn spring-boot:run
cd rate-ingestion-service && mvn spring-boot:run
cd websocket-service && mvn spring-boot:run
```

### Test APIs
```bash
# Swagger UI
open http://localhost:8080/api/v1/swagger-ui.html

# Get FX rate
curl http://localhost:8080/api/v1/rates/EUR/USD

# Batch request
curl -X POST http://localhost:8080/api/v1/rates/batch \
  -H "Content-Type: application/json" \
  -d '{"currencyPairs": ["EURUSD", "GBPUSD"]}'
```

---

## 📁 Project Structure

```
fx-rates-system/
├── common-lib/              → Shared library (build first!)
├── fx-rates-api/            → Microservice #1 (REST API)
├── rate-ingestion-service/  → Microservice #2 (Fetch rates)
├── websocket-service/       → Microservice #3 (Real-time push)
├── k8s/                     → Kubernetes manifests
└── PROJECT-CONTEXT.md       → COMPLETE CONTEXT (read this!)
```

---

## 🔑 Key Facts

**Architecture Type:** ✅ Independent Microservices (NOT multi-module)

**Services:**
1. **fx-rates-api** (8080) - REST API with caching
2. **rate-ingestion-service** (8081) - Fetch & publish rates
3. **websocket-service** (8082) - Real-time push

**Each service:**
- Has its own `spring-boot-starter-parent`
- Builds independently
- Deploys independently
- Scales independently

---

## 📚 Important Files to Review

### Before Interview:
1. `PROJECT-CONTEXT.md` ← **START HERE**
2. `README.md`
3. `Knowledge/files/presentation-guide.md`
4. `Knowledge/files/fx_rates_architecture.png`

### Architecture:
- `MICROSERVICES-ARCHITECTURE.md`
- `IMPLEMENTATION-SUMMARY.md`

### Changes Made:
- `RESTRUCTURE-SUMMARY.md` (multi-module → independent)
- `POM-FIX-SUMMARY.md` (POM issues fixed)

---

## 🐛 Troubleshooting

**IDE not recognizing project?**
→ Close IDE, delete `.idea`, reimport as Maven project

**Can't find common-lib?**
→ `cd common-lib && mvn clean install`

**Redis connection failed?**
→ `docker run -d -p 6379:6379 redis:7-alpine`

---

## 🎯 Interview Demo Checklist

- [ ] Read `PROJECT-CONTEXT.md`
- [ ] Review `Knowledge/files/presentation-guide.md`
- [ ] Test build: `./build-all.sh`
- [ ] Run locally: `docker-compose up --build`
- [ ] Open Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
- [ ] Test API endpoints
- [ ] Review Kubernetes manifests in `k8s/base/`
- [ ] Practice 2-3 minute presentation

---

## 💡 Key Talking Points

1. **"True microservices architecture"** - independently deployable
2. **"Production-ready code"** - not just diagrams
3. **"Cloud-native with Azure"** - Cosmos DB, Event Hubs, Redis, AKS
4. **"Resilience patterns"** - Circuit breakers, retries, fallbacks
5. **"Multiple scaling mechanisms"** - HPA, KEDA, caching

---

## 🚀 You're Ready!

You have a complete, working implementation that goes beyond the assignment requirements. Good luck! 🍀

**Questions?** Check `PROJECT-CONTEXT.md` for full details.
