package de.dahaem.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the REST API. A thin web layer over the existing services: controllers
 * translate HTTP to service calls and service results to DTOs, nothing more.
 * The console runners (de.dahaem.Main, StoreImportRunner, ...) stay separate entry points.
 *
 * <p>The database is MariaDB, configured from the {@code DAHAEM_DB_URL}, {@code DAHAEM_DB_USER}
 * and {@code DAHAEM_DB_PASSWORD} environment variables (see {@code de.dahaem.shared.Database}).
 */
@SpringBootApplication
public class DahaemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DahaemApplication.class, args);
    }
}
