package de.dahaem.stores;

import java.io.IOException;

/** Penny's own weekly offers; prices depend on the store's region, so the scope must name the store. */
public class PennySource implements OfferSource {

    private final PennyClient client;

    public PennySource(PennyClient client) {
        this.client = client;
    }

    @Override
    public String storeName() {
        return StoreImportService.PENNY;
    }

    @Override
    public String sourceTag() {
        return StoreImportService.SRC_STORE;
    }

    @Override
    public void requireScope(OfferScope scope) {
        if (scope.marketId() == null || scope.marketId().isEmpty()) {
            throw new IllegalStateException("Set penny.market in config/stores.properties");
        }
    }

    @Override
    public OfferPull fetchOffers(OfferScope scope) throws IOException {
        return client.fetchOffers(scope.marketId());
    }
}
