# syntax=docker/dockerfile:1.7
#
# RentManager backend — production image.
# Tests are not run here: CI runs `mvn verify` (unit + Testcontainers) on
# every push, and an image build has no Docker daemon for Testcontainers.

# ── Build ────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl . dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl . package -DskipTests \
 && cp target/rentmanager-backend-*.jar /src/app.jar \
 && java -Djarmode=layertools -jar /src/app.jar extract --destination /src/layers

# ── Runtime ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
# Unprivileged, fixed uid so volume ownership is predictable.
RUN groupadd --system --gid 10001 rentmanager \
 && useradd --system --uid 10001 --gid rentmanager --home /app --shell /usr/sbin/nologin rentmanager
WORKDIR /app

# Dependency layers first: they change rarely, so redeploys push only the
# application layer.
COPY --from=build /src/layers/dependencies/ ./
COPY --from=build /src/layers/spring-boot-loader/ ./
COPY --from=build /src/layers/snapshot-dependencies/ ./
COPY --from=build /src/layers/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod \
    APP_DEPLOYMENT_STRICT=true \
    API_DOCS_ENABLED=false \
    FORWARD_HEADERS_STRATEGY=native \
    SERVER_PORT=8080 \
    MANAGEMENT_PORT=8081 \
    TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

USER 10001:10001
EXPOSE 8080
# 8081 (health, metrics) is for the container network only — never publish it.

# No curl in the JRE image; bash's /dev/tcp is enough for a readiness probe.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8081 && printf "GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
