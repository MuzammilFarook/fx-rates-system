# Independent Microservice Dockerfile for FX Rates API
# Multi-stage build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy common-lib and build it first (shared dependency)
COPY common-lib/pom.xml ./common-lib/pom.xml
COPY common-lib/src ./common-lib/src
RUN cd common-lib && mvn clean install -DskipTests

# Copy and build fx-rates-api
COPY fx-rates-api/pom.xml ./fx-rates-api/pom.xml
RUN cd fx-rates-api && mvn dependency:go-offline -B

COPY fx-rates-api/src ./fx-rates-api/src
RUN cd fx-rates-api && mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy jar from build stage
COPY --from=build /build/fx-rates-api/target/*.jar app.jar

# Change ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/rates/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
