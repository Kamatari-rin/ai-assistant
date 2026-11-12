FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

COPY ai-service/pom.xml ai-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml

RUN chmod +x mvnw

COPY order-service/src/ order-service/src/

RUN ./mvnw -B -ntp \
    -pl order-service -am \
    clean verify

FROM eclipse-temurin:21-jre-noble AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app \
        --home-dir /app --no-create-home app \
        --shell /usr/sbin/nologin

WORKDIR /app

COPY --from=build --chown=app:app \
    /workspace/order-service/target/order-service-0.0.1-SNAPSHOT.jar \
    app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
