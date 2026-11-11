package de.dahaem.stores;

import java.util.List;

/** The stores and brands DAHÄM has, for pickers. No SQL here, no UI here. */
public class StoreCatalogService {

    private final StoreRepository repository;

    public StoreCatalogService(StoreRepository repository) {
        this.repository = repository;
    }

    public List<StoreRef> stores() {
        return repository.listStores();
    }

    /** One page of brands whose name contains {@code query} (blank = all), by name. */
    public BrandPage brands(String query, long offset, int limit) {
        return repository.searchBrands(query == null || query.isBlank() ? null : query.trim(), offset, limit);
    }
}
