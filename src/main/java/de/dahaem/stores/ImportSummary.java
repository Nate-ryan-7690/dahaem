package de.dahaem.stores;

import java.util.List;

/** What one import run did; printed by StoreImportRunner. status is OK, PARTIAL or FAILED. */
public record ImportSummary(String store, String kind, String status, int pages, int items,
                            List<CatalogChange> changes, String notes) {
}
