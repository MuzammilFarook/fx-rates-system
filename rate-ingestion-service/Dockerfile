# Independent Microservice Dockerfile for Rate Ingestion Service
# Multi-stage build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy common-lib and build it first (shared dependency)
COPY common-lib/pom.xml ./common-lib/pom.xml
COPY common-lib/src ./common-lib/src
RUN cd common-lib && mvn clean install -DskipTests

# Copy and build rate-ingestion-service
COPY rate-ingestion-service/pom.xml ./rate-ingestion-service/pom.xml
RUN cd rate-ingestion-service && mvn dependency:go-offline -B

COPY rate-ingestion-service/src ./rate-ingestion-service/src
RUN cd rate-ingestion-service && mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy jar from build stage
COPY --from=build /build/rate-ingestion-service/target/*.jar app.jar

# Change ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
