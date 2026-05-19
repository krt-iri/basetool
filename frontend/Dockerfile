FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Application version string forwarded to Gradle's `bootBuildInfo` via
# `-PappVersion=...`. `.dockerignore` excludes `.git` from the build context
# (intentional — keeps the build context small and is consistent with secrets
# policy), so `git describe` inside the container has nothing to work with.
# Instead the CI workflow computes the value on the runner (where `.git` is
# fully checked out with `fetch-depth: 0`) and passes it through as a
# `--build-arg`. Unset (= local `docker build` without the arg) falls back to
# the Gradle project version inside `frontend/build.gradle.kts`.
ARG APP_VERSION=

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

# Quellcode kopieren und Anwendung bauen.
# `-x test` skips the test suite; `-x checkstyleMain -x spotbugsMain
# -x spotlessCheck -x spotlessJavaCheck` skip the static-analysis and formatting
# verification steps that the `check` task pulls in via `build`. Those tools are
# meant for developer / CI workflows — running them inside the production image
# build only adds minutes and needs auxiliary files (`config/checkstyle/`) that
# are not part of the Docker context. Spotless in particular fails the build
# when source files were checked out with CRLF line endings (typical on a
# Windows-developer's workstation that pushes to a Linux build host), even
# though the byte content is otherwise identical to the LF copy in git. CI
# runs `./gradlew check` separately on a controlled environment where format
# enforcement is the right call.
COPY frontend/src/ frontend/src/
# `${APP_VERSION:+...}` only expands when the build-arg was set and non-empty,
# so a `docker build` without `--build-arg APP_VERSION=...` still works and the
# Gradle fallback in `frontend/build.gradle.kts` kicks in.
RUN ./gradlew :frontend:build \
    ${APP_VERSION:+-PappVersion=$APP_VERSION} \
    -x test \
    -x checkstyleMain \
    -x spotbugsMain \
    -x spotlessCheck \
    -x spotlessJavaCheck \
    --no-daemon

# Layered JAR extrahieren
WORKDIR /app/frontend/build/libs
RUN JAR_FILE=$(find . -maxdepth 1 -name "frontend-*.jar" ! -name "*-plain.jar" ! -name "*-sources.jar" | head -n 1) && \
    cp $JAR_FILE /app/app.jar

WORKDIR /app
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

WORKDIR /app/extracted

# Finales Image — Alpine variant of Temurin's JRE (~80 MB compressed, ~170 MB
# smaller than the jammy base). musl libc instead of glibc; safe here because
# nothing in the frontend reaches outside the JVM via JNI — Thymeleaf SSR,
# WebClient (Reactor Netty), Spring Session + Lettuce (Redis client) are all
# pure-Java. If we ever add a native library that depends on glibc, revisit
# and switch back to `25-jre-jammy`.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root runtime user. Fixed UID/GID so host bind mounts (e.g. the log
# directory mounted in docker-compose) can be pre-chowned consistently:
#   chown -R 10001:10001 /var/iri/frontend/log
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

EXPOSE 18081

# Hit the Spring Boot Actuator readiness probe. The frontend serves HTTPS with a
# self-signed certificate (see application.yml `server.ssl`) so curl runs with -k
# to skip cert verification. `start-period=60s` covers the JVM warm-up window so
# Compose does not flap the container into `unhealthy` while Tomcat is still
# binding the port.
#
# Why /actuator/health/readiness instead of /actuator/health:
#   1. Spring's `readinessState` indicator only goes UP after ApplicationReadyEvent,
#      so a 200 here guarantees the context is fully initialised (OAuth2 client
#      registrations resolved, Spring Session ready, scheduled tasks armed) —
#      not just "Tomcat is bound".
#   2. The `readiness` health group in application.yml widens the set of indicators
#      contributing to this endpoint (backend, keycloak, redis, diskSpace, ssl),
#      so a backend or Keycloak outage drops readiness DOWN and any orchestrator
#      gating on `service_healthy` will not route traffic to this instance.
# Liveness (/actuator/health/liveness) stays unused here on purpose — a transient
# backend/Keycloak blip should yank this instance out of the rotation, not restart it.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsSk https://localhost:18081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
