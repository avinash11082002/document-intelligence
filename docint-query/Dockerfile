# ==================== Build Stage ====================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY docint-common ./docint-common
COPY docint-api ./docint-api
COPY docint-worker ./docint-worker
COPY docint-query ./docint-query
RUN mvn clean package -pl docint-query -am -DskipTests -B

# ==================== Runtime Stage ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/docint-query/target/*.jar app.jar

RUN addgroup -S docint && adduser -S docint -G docint
RUN chown -R docint:docint /app
USER docint

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
