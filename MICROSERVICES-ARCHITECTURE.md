# Independent Microservices Architecture

## ✅ True Microservices Implementation

This project implements **true microservices architecture** where each service is:
- **Independently deployable**
- **Independently buildable**
- **Independently scalable**
- **Owns its own Spring Boot parent**

## 🏗️ Structure

```
fx-rates-system/
├── common-lib/                  # Shared library (installed to local Maven repo)
│   ├── pom.xml                 # Independent Spring Boot parent
│   └── src/...                 # Shared models, DTOs, events
│
├── fx-rates-api/               # Independent Microservice #1
│   ├── pom.xml                 # Own Spring Boot parent
│   ├── Dockerfile
│   └── src/...                 # REST API service
│
├── rate-ingestion-service/     # Independent Microservice #2
│   ├── pom.xml                 # Own Spring Boot parent
│   ├── Dockerfile
│   └── src/...                 # Rate ingestion service
│
├── websocket-service/          # Independent Microservice #3
│   ├── pom.xml                 # Own Spring Boot parent
│   ├── Dockerfile
│   └── src/...                 # WebSocket service
│
├── build-all.sh                # Build all services
└── docker-compose.yml          # Local development
```

## 📊 Key Differences from Multi-Module

| Aspect | Multi-Module | Independent Microservices ✅ |
|--------|-------------|----------------------------|
| **Parent POM** | Shared parent | Each has Spring Boot parent |
| **Build** | `mvn install` (all together) | Build each separately |
| **Versioning** | Same version | Independent versioning |
| **Deployment** | Deploy together | Deploy independently |
| **Scaling** | Scale together | Scale independently |
| **Repository** | One repo (mono-repo) | Can be separate repos |

## 🚀 Building the Services

### Option 1: Build All at Once (Convenience Script)

```bash
./build-all.sh
```

This script:
1. Builds `common-lib` and installs to local Maven repo
2. Builds each microservice independently
3. Creates runnable JAR files

### Option 2: Build Individually (True Microservices Way)

```bash
# 1. Build and install common library
cd common-lib
mvn clean install

# 2. Build fx-rates-api (independent)
cd ../fx-rates-api
mvn clean package

# 3. Build rate-ingestion-service (independent)
cd ../rate-ingestion-service
mvn clean package

# 4. Build websocket-service (independent)
cd ../websocket-service
mvn clean package
```

## 🏃 Running Services

### Run Locally (Each Service Independently)

```bash
# Terminal 1 - FX Rates API
cd fx-rates-api
mvn spring-boot:run

# Terminal 2 - Rate Ingestion Service
cd rate-ingestion-service
mvn spring-boot:run

# Terminal 3 - WebSocket Service
cd websocket-service
mvn spring-boot:run
```

### Run with Docker Compose

```bash
docker-compose up --build
```

## 📦 common-lib: Shared Library Pattern

The `common-lib` is a **shared library** (not a microservice). It contains:
- Domain models (`FxRate`)
- DTOs (requests/responses)
- Event models (for Event Hubs)
- Exceptions
- Constants

### Why This Approach?

**Advantages:**
✅ Avoids code duplication
✅ Ensures consistent data models across services
✅ Simplifies communication between services
✅ Still allows independent deployment (services just depend on a library version)

**Best Practices:**
- Keep it **lightweight** (models only, no business logic)
- Version it independently
- In production, publish to Maven Central or Artifactory
- Each microservice specifies which version to use

### Alternative: Duplication Pattern

Some teams prefer **duplicating** models in each service for complete independence. Trade-offs:

| Shared Library ✅ | Duplication |
|------------------|-------------|
| ✅ Less code | ✅ Complete independence |
| ✅ Consistency | ✅ No shared dependency |
| ❌ Shared dependency | ❌ Code duplication |
| ❌ Coordination needed | ❌ Potential inconsistency |

For this project, **shared library** is the pragmatic choice given the common domain models.

## 🔄 Independent Versioning

Each microservice has its own version:

```xml
<!-- fx-rates-api/pom.xml -->
<groupId>com.fexco</groupId>
<artifactId>fx-rates-api</artifactId>
<version>1.0.0-SNAPSHOT</version>

<!-- rate-ingestion-service/pom.xml -->
<groupId>com.fexco</groupId>
<artifactId>rate-ingestion-service</artifactId>
<version>1.0.0-SNAPSHOT</version>

<!-- websocket-service/pom.xml -->
<groupId>com.fexco</groupId>
<artifactId>websocket-service</artifactId>
<version>1.0.0-SNAPSHOT</version>
```

**Benefits:**
- Update `fx-rates-api` to `1.1.0` without touching other services
- Deploy services on different schedules
- Rollback individual services

## 🐳 Docker: Independent Images

Each service has its own Dockerfile:

```dockerfile
# fx-rates-api/Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy ONLY what this service needs
COPY common-lib/ ./common-lib/
COPY fx-rates-api/ ./fx-rates-api/

# Build common-lib first
RUN cd common-lib && mvn clean install -DskipTests

# Build this service
RUN cd fx-rates-api && mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/fx-rates-api/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## ☸️ Kubernetes: Independent Deployments

Each service has its own:
- Deployment
- Service
- HPA/KEDA scaling
- ConfigMap
- Secrets (if needed)

```bash
# Deploy services independently
kubectl apply -f k8s/base/fx-rates-api-deployment.yaml
kubectl apply -f k8s/base/rate-ingestion-deployment.yaml
kubectl apply -f k8s/base/websocket-deployment.yaml

# Scale independently
kubectl scale deployment fx-rates-api --replicas=10
kubectl scale deployment websocket-service --replicas=5
```

## 🎯 Interview Talking Points

**When discussing the architecture:**

1. **"I've implemented true microservices, not a monolith"**
   - Each service has independent lifecycle
   - Can deploy and scale separately
   - Different teams could own different services

2. **"The shared library is a pragmatic trade-off"**
   - Avoids duplication of domain models
   - Still maintains service independence
   - In production, would be published to Maven repo

3. **"Services communicate via events (Event Hubs)"**
   - Loose coupling
   - Async communication
   - Services don't call each other directly

4. **"Each service can be versioned independently"**
   - Update API service without touching WebSocket service
   - Independent release cycles
   - Easier to maintain

## 📚 Further Reading

- [Microservices Patterns by Chris Richardson](https://microservices.io/)
- [Spring Boot Microservices Best Practices](https://spring.io/microservices)
- [Martin Fowler - Microservices](https://martinfowler.com/articles/microservices.html)

---

## Summary

✅ **Independent microservices** (not multi-module)
✅ Each service has its own Spring Boot parent
✅ Build and deploy independently
✅ Scale independently
✅ True microservices architecture
