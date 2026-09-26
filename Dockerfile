# ==================== Build Stage ====================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ==================== Runtime Stage ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create upload directory
RUN mkdir -p /data/uploads

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Non-root user for security
RUN addgroup -S docint && adduser -S docint -G docint
RUN chown -R docint:docint /app /data
USER docint

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
