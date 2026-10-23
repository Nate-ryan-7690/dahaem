package de.dahaem.stores;

import java.io.IOException;

/**
 * One place weekly offers can be pulled from: a chain's own site or one chain on an aggregator.
 * {@link StoreImportService#importOffers(OfferSource, OfferScope)} runs the shared flow (store row,
 * run row, save, status); a source only knows how to fetch and what to call itself.
 * New sources are registered in {@link OfferSourceFactory}.
 */
public interface OfferSource {

    /** Name EXACTLY as the row in the `store` table, or a duplicate store is created. */
    String storeName();

    /** Provenance stored on store_offer.store_offer_source (StoreImportService.SRC_*). */
    String sourceTag();

    /** Throws before any database row is written if the scope lacks what this source needs. */
    default void requireScope(OfferScope scope) {
    }

    OfferPull fetchOffers(OfferScope scope) throws IOException;

    /** Message of the RuntimeException a failed fetch is reported as. */
    default String failureMessage(IOException e) {
        return storeName() + " offer import failed: " + e.getMessage();
    }
}
