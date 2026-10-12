FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY . .
RUN mvn -B -DskipTests -pl service-oa -am package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /build/service-oa/target/service-oa.jar /app/service-oa.jar

USER app
EXPOSE 8800
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=18 \
    CMD curl --fail --silent http://127.0.0.1:8800/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/service-oa.jar"]
