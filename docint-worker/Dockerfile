# ==================== Build Stage ====================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY docint-common ./docint-common
COPY docint-api ./docint-api
COPY docint-worker ./docint-worker
COPY docint-query ./docint-query
RUN mvn clean package -pl docint-worker -am -DskipTests -B

# ==================== Runtime Stage ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN mkdir -p /data/uploads
COPY --from=build /app/docint-worker/target/*.jar app.jar

RUN addgroup -S docint && adduser -S docint -G docint
RUN chown -R docint:docint /app /data
USER docint

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
