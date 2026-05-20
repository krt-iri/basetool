FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Declared (but unused) so the shared CI matrix in `release-images.yml` can
# pass `--build-arg APP_VERSION=...` to both module builds without BuildKit
# warning about an unconsumed arg. Only the frontend module renders a version
# label today; the backend is API-only and has no UI to surface it.
ARG APP_VERSION=

# Gradle-Wrapper und Konfigurationsdateien für Dependency-Caching kopieren.
# `--chmod=755` on the wrapper copy avoids a separate `RUN chmod +x gradlew`
# layer — BuildKit applies the bits at COPY time.
COPY --chmod=755 gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY versions.properties .
COPY gradle.properties .
COPY backend/build.gradle.kts backend/
COPY frontend/build.gradle.kts frontend/

# Dependencies herunterladen, um sie im Docker-Cache zu behalten. The previous
# `|| true` masked legitimate failures (wrapper download problems, transient
# Maven Central 5xx) and the next `:build` step would silently re-download
# everything anyway, so the cache layer became worthless without a visible
# error. If this step fails, the build now fails — fix the root cause instead.
RUN ./gradlew :backend:dependencies --no-daemon

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
COPY backend/src/ backend/src/
RUN ./gradlew :backend:build \
    -x test \
    -x checkstyleMain \
    -x spotbugsMain \
    -x spotlessCheck \
    -x spotlessJavaCheck \
    --no-daemon

# Layered JAR extrahieren. The Spring Boot Gradle plugin produces exactly one
# bootable `backend-<version>.jar` alongside `backend-<version>-plain.jar`
# (the lightweight non-bootable classifier) and `backend-<version>-sources.jar`
# (sources artifact from `withSourcesJar()`). The bootable jar is the one
# without a classifier — match it explicitly via two filename rules instead
# of the old `find … | head -n 1` pipeline, which silently picked whichever
# the directory iterator surfaced first. Building the candidate list in a
# loop also lets the script fail loudly when zero or >1 candidates remain
# (e.g. a future change adds another classifier).
WORKDIR /app/backend/build/libs
RUN set -eu; \
    matches=""; \
    for f in backend-*.jar; do \
        case "$f" in \
            *-plain.jar|*-sources.jar) continue ;; \
            *.jar) matches="$matches $f" ;; \
        esac; \
    done; \
    set -- $matches; \
    [ "$#" -eq 1 ] && [ -f "$1" ] || { echo "Expected exactly one bootable backend-*.jar, got $# match(es): '$*'" >&2; ls -la >&2; exit 1; }; \
    cp "$1" /app/app.jar

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
# No `apk add` for curl: BusyBox's built-in `wget` (already in the Alpine
# base) is enough for the HEALTHCHECK, and dropping the apk layer keeps the
# image surface a few MB smaller plus removes one network round-trip on
# rebuild. BusyBox addgroup/adduser (-S = system account, no password, no
# UID/GID range check) replace the Debian groupadd/useradd.
RUN addgroup -S -g 10001 app \
    && adduser  -S -u 10001 -G app -H -h /nonexistent -s /sbin/nologin app \
    && mkdir -p /app/logs \
    && chown -R 10001:10001 /app

# Extrahierte Layer einzeln kopieren (owned by the runtime user)
COPY --from=build --chown=10001:10001 /app/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /app/extracted/application/ ./

# Spring Boot AppCDS training run — writes a class-data-sharing archive next
# to the launcher jar so the runtime can load classes ~30-50% faster (typical
# Spring Boot startup-time reduction; results depend on which beans actually
# load during training).
#
# Context refresh in a fully-configured Spring Boot app triggers eager
# initialisation for infrastructure beans that require live external services
# (Flyway, JPA EntityManagerFactory, OAuth2 issuer metadata fetch). None of
# those are reachable inside the build container, so the disabling flags below
# short-circuit them: Hikari skips the start-up probe
# (`initialization-fail-timeout=-1`), Flyway is off, lazy-initialization
# defers the remaining beans, and the OAuth2 resource-server issuer URI is
# blanked so no `.well-known/openid-configuration` HTTP call is attempted.
#
# Best-effort: if any future refactor breaks context refresh under these
# flags, training fails silently and the runtime ENTRYPOINT below detects
# the missing .jsa file and falls back to a non-CDS startup. Build logs
# carry the JVM stack trace so CI can surface the regression.
RUN java -XX:ArchiveClassesAtExit=/app/application.jsa \
        -Dspring.context.exit=onRefresh \
        -Dspring.main.lazy-initialization=true \
        -Dspring.flyway.enabled=false \
        -Dspring.datasource.hikari.initialization-fail-timeout=-1 \
        -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.security.oauth2.resourceserver.jwt.issuer-uri= \
        -jar app.jar 2>&1 || true ; \
    if [ -f /app/application.jsa ]; then \
        chown 10001:10001 /app/application.jsa ; \
        echo "[CDS] Archive created: $(wc -c < /app/application.jsa) bytes" ; \
    else \
        echo "[CDS] Archive not produced; runtime will start without CDS." ; \
    fi

USER 10001:10001

EXPOSE 11261

# Hit the Spring Boot Actuator readiness probe. The backend serves HTTPS with a
# self-signed certificate (see application.yml `server.ssl`) so wget runs with
# `--no-check-certificate` to skip cert verification.
#
# Timing: `start-period=90s` covers a slow Flyway migration run plus the JVM
# warm-up window — a stricter limit flaps the container into `unhealthy` on
# cold-cache servers even when the app is healthy. `start-interval=5s` (Docker
# 25+) re-probes every 5 s during the start window so a fast boot is reported
# fast, without spamming the runtime probe schedule (interval=30s) afterwards.
#
# Why /actuator/health/readiness instead of /actuator/health:
#   1. Spring's `readinessState` indicator only goes UP after ApplicationReadyEvent,
#      so a 200 here guarantees the context is fully initialised (Flyway done,
#      caches warmed, scheduled tasks armed) — not just "Tomcat is bound".
#   2. The `readiness` health group in application.yml widens the set of indicators
#      contributing to this endpoint (db, keycloak, diskSpace, ssl), so a Keycloak
#      outage or a near-expiry SSL cert drops readiness DOWN and Docker Compose
#      gates the frontend on a real end-to-end check.
# Liveness (/actuator/health/liveness) stays unused here on purpose — a transient
# Keycloak blip should yank the container out of `service_healthy`, not restart it.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --start-interval=5s --retries=3 \
    CMD wget -q --no-check-certificate -O /dev/null https://localhost:11261/actuator/health/readiness || exit 1

# Conditional CDS: if the training run above produced an archive, load it; if
# not, start without CDS so the image still works. The `-jar app.jar` and
# `-XX:MaxRAMPercentage` settings live in JAVA_TOOL_OPTIONS (from compose) so
# they apply in both branches without duplication.
ENTRYPOINT ["sh", "-c", "if [ -f /app/application.jsa ]; then exec java -XX:SharedArchiveFile=/app/application.jsa -jar /app/app.jar; else exec java -jar /app/app.jar; fi"]
