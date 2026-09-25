package de.dahaem.stores;

import java.util.List;

/** Offers pulled from one chain, plus the requests that failed (so a run can be marked PARTIAL). */
public record OfferPull(List<StoreOffer> offers, List<String> failedRequests) {
}
