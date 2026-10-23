package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The factory and the three sources are pure wiring: every client is a mock, so no network. */
class OfferSourceFactoryTest {

    private final AldiNordClient aldi = mock(AldiNordClient.class);
    private final PennyClient penny = mock(PennyClient.class);
    private final AggregatorClient aggregator = new AggregatorClient(mock(HttpFetcher.class));
    private final OfferSourceFactory factory = new OfferSourceFactory(aldi, penny, aggregator);

    @Test
    void knowsTheDirectSourcesAndEveryAggregatorChain() {
        Set<String> keys = factory.keys();
        assertTrue(keys.contains("aldi"));
        assertTrue(keys.contains("penny"));
        for (String chain : AggregatorClient.STORES.keySet()) {
            assertTrue(keys.contains(OfferSourceFactory.aggregatorKey(chain)), chain);
        }
        assertEquals(2 + AggregatorClient.STORES.size(), keys.size());
    }

    @Test
    void unknownKeyListsTheKnownOnes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> factory.create("nope"));
        assertTrue(e.getMessage().contains("nope"));
        assertTrue(e.getMessage().contains("penny"));
    }

    @Test
    void sourcesCarryTheStoreRowNameAndProvenance() {
        assertEquals(StoreImportService.ALDI, factory.create("aldi").storeName());
        assertEquals(StoreImportService.SRC_STORE, factory.create("aldi").sourceTag());
        assertEquals(StoreImportService.PENNY, factory.create("penny").storeName());
        assertEquals(StoreImportService.SRC_STORE, factory.create("penny").sourceTag());
        OfferSource lidl = factory.create(OfferSourceFactory.aggregatorKey("lidl"));
        assertEquals(AggregatorClient.STORES.get("lidl")[0], lidl.storeName());
        assertEquals(StoreImportService.SRC_AGGREGATOR, lidl.sourceTag());
    }

    @Test
    void createReturnsTheSameSourceEachTime() {
        assertSame(factory.create("penny"), factory.create("penny"));
    }

    @Test
    void aldiIgnoresTheScope() {
        OfferPull pull = new OfferPull(List.of(), List.of());
        when(aldi.fetchOffers()).thenReturn(pull);
        assertSame(pull, new AldiNordSource(aldi).fetchOffers(new OfferScope("", "")));
    }

    @Test
    void pennyFetchesForTheScopesMarket() throws IOException {
        OfferPull pull = new OfferPull(List.of(), List.of());
        when(penny.fetchOffers("123")).thenReturn(pull);
        assertSame(pull, new PennySource(penny).fetchOffers(new OfferScope("123", "27356")));
    }

    @Test
    void pennyNeedsAMarketInTheScope() {
        PennySource source = new PennySource(penny);
        assertThrows(IllegalStateException.class, () -> source.requireScope(new OfferScope("", "27356")));
        assertThrows(IllegalStateException.class, () -> source.requireScope(new OfferScope(null, "27356")));
        source.requireScope(new OfferScope("123", ""));       // a market is all it needs
    }

    @Test
    void aggregatorFetchesTheChainForTheScopesPostalCode() throws IOException {
        AggregatorClient client = mock(AggregatorClient.class);
        OfferPull pull = new OfferPull(List.of(), List.of());
        when(client.fetchOffers("famila", "27356")).thenReturn(pull);
        assertSame(pull, new AggregatorSource(client, "famila").fetchOffers(new OfferScope("123", "27356")));
        verify(client).fetchOffers("famila", "27356");
    }
}
