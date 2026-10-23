package de.dahaem.stores;

/** ALDI Nord's own weekly offers (national list, so the scope is ignored). */
public class AldiNordSource implements OfferSource {

    private final AldiNordClient client;

    public AldiNordSource(AldiNordClient client) {
        this.client = client;
    }

    @Override
    public String storeName() {
        return StoreImportService.ALDI;
    }

    @Override
    public String sourceTag() {
        return StoreImportService.SRC_STORE;
    }

    @Override
    public OfferPull fetchOffers(OfferScope scope) {
        return client.fetchOffers();
    }
}
