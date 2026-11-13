FROM eclipse-temurin:21-jdk-noble@sha256:06c1f157dacf6b68198bd9a4186dd4c34ef24e65d7bbbe2a0cf8e62571ad38c9 AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

COPY ai-service/pom.xml ai-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml

RUN chmod +x mvnw

COPY ai-service/src/ ai-service/src/

RUN ./mvnw -B -ntp \
    -pl ai-service -am \
    clean verify

FROM eclipse-temurin:21-jre-noble@sha256:0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515 AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app \
        --home-dir /app --no-create-home app \
        --shell /usr/sbin/nologin

WORKDIR /app

COPY --from=build --chown=app:app \
    /workspace/ai-service/target/ai-service-0.0.1-SNAPSHOT.jar \
    app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
