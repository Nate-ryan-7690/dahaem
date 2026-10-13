# Build the shaded jar, then run it on a slim JRE. The image is meant to sit on an internal
# Docker network behind the PHP front end: it publishes nothing itself (compose must not map
# port 8080 to the host).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies first so they stay cached until the pom changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests need Docker (Testcontainers) and are run by CI, not during the image build.
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home dahaem
WORKDIR /app
COPY --from=build /build/target/dahaem-1.0-SNAPSHOT.jar app.jar
# The store refresh and the receipt import work relative to /app. They read config/ (the overrides are baked in
# here; compose mounts the repo's config/ over it so stores.properties, which is gitignored, is
# there too) and writes its unmatched-item reports to data/stores, which must belong to the
# non-root user. A named volume on /app/data takes over that ownership.
COPY config/store_overrides.json config/receipt_overrides.json config/recipe_tags.properties config/diet_presets.json config/
RUN mkdir -p data/stores && chown -R dahaem data
USER dahaem

# Inside the container the API must listen on all interfaces so other containers can reach it;
# application.properties defaults to loopback for non-container installs. Whether it is reachable
# from the host is decided by the compose file (no `ports:`), not by this setting.
ENV SERVER_ADDRESS=0.0.0.0
# DAHAEM_DB_URL, DAHAEM_DB_USER, DAHAEM_DB_PASSWORD (and INTERNAL_API_TOKEN) come from the environment.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
