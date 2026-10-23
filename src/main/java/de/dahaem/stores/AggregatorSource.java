package de.dahaem.stores;

import java.io.IOException;

/**
 * One chain's offers from the aggregator (SECONDARY source). storeKey is a key in
 * {@link AggregatorClient#STORES}. Tagged AGGREGATOR so it never overwrites a direct pull and
 * ranks below it.
 */
public class AggregatorSource implements OfferSource {

    private final AggregatorClient client;
    private final String storeKey;

    public AggregatorSource(AggregatorClient client, String storeKey) {
        this.client = client;
        this.storeKey = storeKey;
    }

    @Override
    public String storeName() {
        return client.displayName(storeKey);
    }

    @Override
    public String sourceTag() {
        return StoreImportService.SRC_AGGREGATOR;
    }

    @Override
    public OfferPull fetchOffers(OfferScope scope) throws IOException {
        return client.fetchOffers(storeKey, scope.postalCode());
    }

    @Override
    public String failureMessage(IOException e) {
        return "Aggregator offer import failed for " + storeName() + ": " + e.getMessage();
    }
}
