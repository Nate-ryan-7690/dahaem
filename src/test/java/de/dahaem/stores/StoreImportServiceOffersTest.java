package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The shared offer flow with a mocked repository and a fake source: no network, no database. */
class StoreImportServiceOffersTest {

    private static final OfferScope SCOPE = new OfferScope("123", "27356");

    private final StoreRepository repository = mock(StoreRepository.class);
    private final StoreImportService service = new StoreImportService(repository,
            mock(AldiNordClient.class), mock(PennyClient.class), mock(AggregatorClient.class), new StoreConfig());

    /** A source whose fetch returns a fixed pull or throws a fixed exception. */
    private static OfferSource source(String tag, OfferPull pull, IOException failure) {
        return new OfferSource() {
            @Override
            public String storeName() {
                return "Fake";
            }

            @Override
            public String sourceTag() {
                return tag;
            }

            @Override
            public OfferPull fetchOffers(OfferScope scope) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                return pull;
            }
        };
    }

    private static StoreOffer offer() {
        return new StoreOffer("id-1", null, "Butter", "250 g", 1.79, 1, null, 2.29, null, null, null,
                null, "Molkerei", "", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24));
    }

    private void stubRows() {
        when(repository.findOrCreateStore("Fake")).thenReturn(7);
        when(repository.startRun(7, "OFFERS")).thenReturn(42);
    }

    @Test
    void savesTheOffersWithTheSourcesTagAndFinishesOk() {
        stubRows();
        when(repository.saveOffers(eq(7), anyList(), any(LocalDate.class), eq("AGGREGATOR"))).thenReturn(1);

        ImportSummary summary = service.importOffers(
                source("AGGREGATOR", new OfferPull(List.of(offer()), List.of()), null), SCOPE);

        assertEquals("OK", summary.status());
        assertEquals(1, summary.items());
        verify(repository).finishRun(42, "OK", 0, 1, "");
    }

    @Test
    void someFailedRequestsMakeTheRunPartial() {
        stubRows();
        when(repository.saveOffers(eq(7), anyList(), any(LocalDate.class), eq("STORE"))).thenReturn(1);

        ImportSummary summary = service.importOffers(
                source("STORE", new OfferPull(List.of(offer()), List.of("page 2")), null), SCOPE);

        assertEquals("PARTIAL", summary.status());
        assertTrue(summary.notes().contains("page 2"));
        verify(repository).finishRun(eq(42), eq("PARTIAL"), eq(0), eq(1), contains("page 2"));
    }

    @Test
    void noOffersAndEveryRequestFailedClosesTheRunAsFailed() {
        stubRows();
        OfferSource s = source("STORE", new OfferPull(List.of(), List.of("page 1")), null);

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.importOffers(s, SCOPE));

        assertTrue(e.getMessage().contains("no offers pulled"));
        verify(repository).finishRun(eq(42), eq("FAILED"), eq(0), eq(0), contains("no offers pulled"));
    }

    @Test
    void anIoErrorIsReportedWithTheSourcesMessageAndClosesTheRun() {
        stubRows();
        OfferSource s = source("STORE", null, new IOException("boom"));

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.importOffers(s, SCOPE));

        assertEquals("Fake offer import failed: boom", e.getMessage());
        verify(repository).finishRun(eq(42), eq("FAILED"), eq(0), eq(0), contains("boom"));
    }

    @Test
    void anAggregatorIoErrorKeepsTheChainNameInTheMessage() throws IOException {
        AggregatorClient client = mock(AggregatorClient.class);
        when(client.displayName("lidl")).thenReturn("Lidl");
        when(client.fetchOffers("lidl", "27356")).thenThrow(new IOException("down"));
        when(repository.findOrCreateStore("Lidl")).thenReturn(7);
        when(repository.startRun(7, "OFFERS")).thenReturn(42);
        OfferSource s = new AggregatorSource(client, "lidl");

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.importOffers(s, SCOPE));

        assertEquals("Aggregator offer import failed for Lidl: down", e.getMessage());
    }

    @Test
    void aMissingScopeFailsBeforeAnyDatabaseRowIsWritten() {
        OfferSource penny = new PennySource(mock(PennyClient.class));

        assertThrows(IllegalStateException.class,
                () -> service.importOffers(penny, new OfferScope("", "27356")));

        verifyNoInteractions(repository);
    }
}
