FROM eclipse-temurin:25-jdk-alpine AS build
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
RUN ./gradlew :backend:dependencies --no-daemon || true

# Quellcode kopieren und Anwendung bauen.
# `-x test` skips the test suite; `-x checkstyleMain -x spotbugsMain` skips the
# static-analysis steps that the `check` task pulls in via `build`. Those tools
# are meant for developer / CI workflows — running them inside the production
# image build only adds minutes and needs auxiliary files (`config/checkstyle/`)
# that are not part of the Docker context. CI runs `./gradlew check` separately.
COPY backend/src/ backend/src/
RUN ./gradlew :backend:build -x test -x checkstyleMain -x spotbugsMain --no-daemon

# Layered JAR extrahieren
WORKDIR /app/backend/build/libs
RUN JAR_FILE=$(find . -maxdepth 1 -name "backend-*.jar" ! -name "*-plain.jar" ! -name "*-sources.jar" | head -n 1) && \
    cp $JAR_FILE /app/app.jar

WORKDIR /app
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

WORKDIR /app/extracted

# Finales Image — Alpine variant of Temurin's JRE (~80 MB compressed, ~170 MB
# smaller than the jammy base). musl libc instead of glibc; safe here because
# every native bit we ship is pure Java: openpdf renders with its 14 embedded
# Type-1 fonts (BaseFont.HELVETICA et al.) so there is no fontconfig / freetype
# / system-font dependency, no Tomcat APR/Native connector is wired in, and the
# Postgres / Redis clients are pure-Java drivers. If we ever pull in a native
# library that depends on glibc, revisit and switch back to `25-jre-jammy`.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root runtime user. Fixed UID/GID so host bind mounts (e.g. the log
# directory mounted in docker-compose) can be pre-chowned consistently:
#   chown -R 10001:10001 /var/iri/backend/log
# `curl` is installed for the HEALTHCHECK directive at the bottom of this file.
# BusyBox addgroup/adduser (-S = system account, no password, no UID/GID range
# check) replace the Debian groupadd/useradd; apk replaces apt-get.
RUN addgroup -S -g 10001 app \
    && adduser  -S -u 10001 -G app -H -h /nonexistent -s /sbin/nologin app \
    && mkdir -p /app/logs \
    && chown -R 10001:10001 /app \
    && apk add --no-cache curl ca-certificates

# Extrahierte Layer einzeln kopieren (owned by the runtime user)
COPY --from=build --chown=10001:10001 /app/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /app/extracted/application/ ./

USER 10001:10001

EXPOSE 11261

# Hit the Spring Boot Actuator health endpoint. The backend serves HTTPS with a
# self-signed certificate (see application.yml `server.ssl`) so curl runs with -k
# to skip cert verification. `start-period=60s` covers the JVM warm-up window so
# Compose does not flap the container into `unhealthy` while Tomcat is still
# binding the port and Flyway is running.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsSk https://localhost:11261/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
