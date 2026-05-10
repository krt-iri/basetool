FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /app

# Gradle-Wrapper und Konfigurationsdateien für Dependency-Caching kopieren
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY versions.properties .
COPY gradle.properties .
COPY backend/build.gradle.kts backend/
COPY frontend/build.gradle.kts frontend/

RUN chmod +x gradlew

# Dependencies herunterladen, um sie im Docker-Cache zu behalten
RUN ./gradlew :frontend:dependencies --no-daemon || true

# Quellcode kopieren und Anwendung bauen
COPY frontend/src/ frontend/src/
RUN ./gradlew :frontend:build -x test --no-daemon

# Layered JAR extrahieren
WORKDIR /app/frontend/build/libs
RUN JAR_FILE=$(find . -maxdepth 1 -name "frontend-*.jar" ! -name "*-plain.jar" ! -name "*-sources.jar" | head -n 1) && \
    cp $JAR_FILE /app/app.jar

WORKDIR /app
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

WORKDIR /app/extracted

# Finales Image
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Non-root runtime user. Fixed UID/GID so host bind mounts (e.g. the log
# directory mounted in docker-compose) can be pre-chowned consistently:
#   chown -R 10001:10001 /var/iri/frontend/log
RUN groupadd --system --gid 10001 app \
    && useradd  --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app/logs \
    && chown -R 10001:10001 /app

# Extrahierte Layer einzeln kopieren (owned by the runtime user)
COPY --from=build --chown=10001:10001 /app/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /app/extracted/application/ ./

USER 10001:10001

EXPOSE 18081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
