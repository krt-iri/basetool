# Profit Basetool — Keycloak provider-JAR artifact image.
#
# A `FROM scratch` artifact whose ONLY purpose is to ship the Keycloak custom
# provider JAR (the Discord federation SPI + first-login membership / account-
# existence gate) the production deploy stages into
# /var/iri/code/keycloak/providers. It carries no executable layer and no base
# image — the server never *runs* it. `scripts/deploy.sh` extracts
# /providers/keycloak-spi.jar out of it with `docker create` + `docker cp` and
# installs it as the mounted provider JAR (REQ-OPS-007, ADR-0055).
#
# Why a SEPARATE artifact from basetool-config: REQ-OPS-005 bars
# keycloak/providers JARs from the config bundle (to keep that package free of
# anything host-specific / secret-shaped), so the provider JAR rides its own
# promotable, cosign-signed channel instead — the same pull-only, digest-pinned,
# deliberately-promoted GHCR channel as the app images and the config bundle, so
# the host needs no new tool and no new credential.
#
# The JAR is built natively in release-images.yml (`./gradlew :keycloak-spi:jar`,
# Java-21 bytecode so it loads under Keycloak's JDK) and copied to the build
# context root as `keycloak-spi.jar` BEFORE this image is built — the final stage
# is a single COPY. It cannot be COPYed straight from keycloak-spi/build/libs
# because `.dockerignore` bars `**/build` from the build context. The JAR is pure
# bytecode (architecture-independent), so the multi-arch index just carries the
# identical layer per platform for parity with the other artifacts.
FROM scratch

COPY keycloak-spi.jar /providers/keycloak-spi.jar
