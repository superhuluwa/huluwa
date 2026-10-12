# syntax=docker/dockerfile:1

# Build the Vue application into Spring Boot's static resource directory.
FROM node:22-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# Package the backend with the freshly built frontend (no frontend Maven profile).
FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -DskipTests dependency:go-offline
COPY src/ ./src/
COPY --from=frontend-build /workspace/src/main/resources/static/ ./src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app
ENV TZ=Asia/Shanghai \
    SERVER_PORT=8000 \
    HULUWA_DATA_DIR=/app/data \
    HOME=/app/data \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai"

# Named volumes inherit this directory's ownership on first use.
RUN groupadd --gid 10001 huluwa \
    && useradd --uid 10001 --gid huluwa --no-create-home \
        --home-dir /app --shell /usr/sbin/nologin huluwa \
    && mkdir -p /app/data \
    && chown huluwa:huluwa /app/data

COPY --from=backend-build /workspace/target/huluwa-console.jar /app/app.jar

USER 10001:10001
VOLUME ["/app/data"]
EXPOSE 8000

# This public endpoint also queries SQLite and works before initial setup.
# curl is already included in the Temurin Ubuntu runtime image.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent --show-error --max-time 3 \
        "http://127.0.0.1:${SERVER_PORT:-8000}/api/auth/status" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
