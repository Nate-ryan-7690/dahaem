package de.dahaem.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the REST API. A thin web layer over the existing services: controllers
 * translate HTTP to service calls and service results to DTOs, nothing more.
 * The console runners (de.dahaem.Main, StoreImportRunner, ...) stay separate entry points.
 *
 * <p>The database is still chosen by the {@code dahaem.db.path} system property
 * (default {@code data/kitchen_organizer.db}), e.g.
 * {@code java -Ddahaem.db.path=/srv/dahaem/kitchen.db -jar dahaem-1.0-SNAPSHOT.jar}.
 */
@SpringBootApplication
public class DahaemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DahaemApplication.class, args);
    }
}
