FROM maven:3.9.9-eclipse-temurin-21 AS build

# 可选镜像：受限网络下把 Maven Central 换成可达的镜像仓库，留空则保持默认。
ARG MAVEN_MIRROR_URL=""

WORKDIR /build
COPY . .
# 只缓存 repository：settings.xml 若一起缓存，未传镜像源的构建会沿用上一次的镜像配置。
RUN --mount=type=cache,target=/root/.m2/repository set -eu; \
    if [ -n "$MAVEN_MIRROR_URL" ]; then \
      mkdir -p /root/.m2; \
      printf '<settings><mirrors><mirror><id>build-mirror</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>\n' "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; \
    fi; \
    mvn -B -DskipTests -pl service-oa -am package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /var/lib/guigu-oa/process-definitions \
    && chown -R app:app /var/lib/guigu-oa
COPY --from=build --chown=app:app /build/service-oa/target/service-oa.jar /app/service-oa.jar

USER app
EXPOSE 8800
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=18 \
    CMD curl --fail --silent http://127.0.0.1:8800/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/service-oa.jar"]
