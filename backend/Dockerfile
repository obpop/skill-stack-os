# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:22-jre-jammy AS runtime
ARG RELEASE_VERSION=local
LABEL org.opencontainers.image.title="Skill Stack backend" \
      org.opencontainers.image.revision="${RELEASE_VERSION}"
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 skillstack \
    && useradd --system --uid 10001 --gid skillstack --no-create-home skillstack
WORKDIR /app
COPY --from=build --chown=skillstack:skillstack /workspace/target/skill-stack-backend-*.jar /app/skill-stack.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=20 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=6 CMD ["curl", "--fail", "--silent", "--show-error", "http://127.0.0.1:8080/actuator/health/readiness"]
ENTRYPOINT ["java", "-jar", "/app/skill-stack.jar"]
