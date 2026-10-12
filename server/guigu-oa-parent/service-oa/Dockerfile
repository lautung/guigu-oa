FROM eclipse-temurin:21-jre

WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --chown=app:app target/service-oa.jar /app/service-oa.jar

USER app
EXPOSE 8800
ENTRYPOINT ["java", "-jar", "/app/service-oa.jar"]
