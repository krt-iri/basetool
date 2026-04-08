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

# Extrahierte Layer einzeln kopieren
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

EXPOSE 18081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
