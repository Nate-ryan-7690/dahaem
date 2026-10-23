package de.dahaem.stores;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates the {@link OfferSource} for a source key: "aldi", "penny" and "aggregator:&lt;chain&gt;" for
 * every chain in {@link AggregatorClient#STORES}. Adding a chain is one OfferSource class plus one
 * register() call here; the import flow in StoreImportService does not change.
 */
public class OfferSourceFactory {

    public static final String ALDI = "aldi";
    public static final String PENNY = "penny";

    private final Map<String, OfferSource> sources = new LinkedHashMap<>();

    public OfferSourceFactory(AldiNordClient aldi, PennyClient penny, AggregatorClient aggregator) {
        register(ALDI, new AldiNordSource(aldi));
        register(PENNY, new PennySource(penny));
        for (String chain : AggregatorClient.STORES.keySet()) {
            register(aggregatorKey(chain), new AggregatorSource(aggregator, chain));
        }
    }

    /** Key of one aggregator chain; chain is a key in {@link AggregatorClient#STORES}. */
    public static String aggregatorKey(String chain) {
        return "aggregator:" + chain;
    }

    public OfferSource create(String key) {
        OfferSource source = sources.get(key);
        if (source == null) {
            throw new IllegalArgumentException("unknown offer source: " + key + " (known: " + sources.keySet() + ")");
        }
        return source;
    }

    public Set<String> keys() {
        return Set.copyOf(sources.keySet());
    }

    private void register(String key, OfferSource source) {
        sources.put(key, source);
    }
}
