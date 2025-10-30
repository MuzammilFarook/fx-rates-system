# Independent Microservice Dockerfile for WebSocket Service
# Multi-stage build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy common-lib and build it first (shared dependency)
COPY common-lib/pom.xml ./common-lib/pom.xml
COPY common-lib/src ./common-lib/src
RUN cd common-lib && mvn clean install -DskipTests

# Copy and build websocket-service
COPY websocket-service/pom.xml ./websocket-service/pom.xml
RUN cd websocket-service && mvn dependency:go-offline -B

COPY websocket-service/src ./websocket-service/src
RUN cd websocket-service && mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy jar from build stage
COPY --from=build /build/websocket-service/target/*.jar app.jar

# Change ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

EXPOSE 8082

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
