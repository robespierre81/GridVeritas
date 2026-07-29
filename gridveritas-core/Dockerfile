# ============================================================
# GridVeritas Core – Multi-stage Dockerfile
# Spring Boot 3.3 / Java 21
# ============================================================

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Install Maven
RUN apt-get update && \
    apt-get install -y --no-install-recommends maven && \
    rm -rf /var/lib/apt/lists/*

# Copy Maven descriptor first (better layer caching)
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn -B dependency:go-offline -DskipTests

# Copy source and build
COPY src ./src
RUN mvn -B -DskipTests package && \
    # Normalize the JAR name for the next stage
    cp target/gridveritas-core-*.jar /workspace/app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="Oliver Bodemer" \
      project="GridVeritas" \
      component="core" \
      version="0.1.0-SNAPSHOT"

# Install curl for HEALTHCHECK and create non-root user
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system gridveritas && \
    useradd --system --gid gridveritas --home-dir /app --shell /sbin/nologin gridveritas

WORKDIR /app

# Copy the built JAR
COPY --from=build /workspace/app.jar app.jar

# Ownership
RUN chown gridveritas:gridveritas app.jar

USER gridveritas

# Default Spring Boot port
EXPOSE 8080

# Optional JVM options can be passed via JAVA_OPTS
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Health check via Spring Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
