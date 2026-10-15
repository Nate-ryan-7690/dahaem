package de.dahaem.api;

import org.springframework.boot.SpringApplication;
import de.dahaem.stores.RefreshConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Entry point for the REST API. A thin web layer over the existing services: controllers
 * translate HTTP to service calls and service results to DTOs, nothing more.
 * The console runners (StoreImportRunner, ReceiptImportRunner, ...) stay separate entry points.
 *
 * <p>The database is MariaDB, configured from the {@code DAHAEM_DB_URL}, {@code DAHAEM_DB_USER}
 * and {@code DAHAEM_DB_PASSWORD} environment variables (see {@code de.dahaem.shared.Database}).
 */
@SpringBootApplication
@Import(RefreshConfig.class)   // opt-in scheduled store refresh (dahaem.refresh.enabled)
public class DahaemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DahaemApplication.class, args);
    }
}
