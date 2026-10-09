package de.dahaem.stores;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pure orchestration tests: every service is a mock, so no network and no database. */
class RefreshServiceTest {

    private final StoreImportService importer = mock(StoreImportService.class);
    private final StoreMatchService matcher = mock(StoreMatchService.class);
    private final StorePromotionService promoter = mock(StorePromotionService.class);
    private final StoreDiscountService discounts = mock(StoreDiscountService.class);
    private final RefreshService refresh = new RefreshService(importer, () -> matcher, () -> promoter, () -> discounts);

    private static ImportSummary ok(String store, String kind) {
        return new ImportSummary(store, kind, "OK", 0, 1, List.of(), "");
    }

    private void stubAllOk() {
        when(importer.importAldiOffers()).thenReturn(ok("Aldi", "offers"));
        when(importer.importPennyOffers()).thenReturn(ok("Penny", "offers"));
        when(importer.importAggregatorOffers(anyString())).thenReturn(ok("agg", "offers"));
        when(importer.importAldiRange()).thenReturn(ok("Aldi", "range"));
        when(matcher.matchStore(anyString(), any(Path.class))).thenReturn(ok("Aldi", "match"));
        when(promoter.promote()).thenReturn(ok("all", "promote"));
        when(discounts.promoteOffers(isNull(), any(Path.class))).thenReturn(ok("all", "discounts"));
    }

    @Test
    void weeklyRunsOffersThenAggregatorsThenDiscounts() {
        stubAllOk();

        RefreshSummary summary = refresh.refreshWeekly();

        InOrder order = inOrder(importer, discounts);
        order.verify(importer).importAldiOffers();
        order.verify(importer).importPennyOffers();
        for (String store : AggregatorClient.STORES.keySet()) {
            order.verify(importer).importAggregatorOffers(store);
        }
        order.verify(discounts).promoteOffers(isNull(), any(Path.class));
        verifyNoInteractions(matcher, promoter);
        assertTrue(summary.allOk());
        assertEquals(2 + AggregatorClient.STORES.size() + 1, summary.results().size());
    }

    @Test
    void monthlyRunsRangeMatchPromoteDiscounts() {
        stubAllOk();

        RefreshSummary summary = refresh.refreshMonthly();

        InOrder order = inOrder(importer, matcher, promoter, discounts);
        order.verify(importer).importAldiRange();
        order.verify(matcher).matchStore(anyString(), any(Path.class));
        order.verify(promoter).promote();
        order.verify(discounts).promoteOffers(isNull(), any(Path.class));
        assertTrue(summary.allOk());
        assertEquals(4, summary.results().size());
    }

    @Test
    void weeklyKeepsGoingWhenAStepThrows() {
        stubAllOk();
        when(importer.importAldiOffers()).thenThrow(new RuntimeException("aldi down"));
        String firstAgg = AggregatorClient.STORES.keySet().iterator().next();
        when(importer.importAggregatorOffers(firstAgg)).thenThrow(new RuntimeException("agg down"));

        RefreshSummary summary = refresh.refreshWeekly();

        verify(importer).importPennyOffers();
        verify(discounts).promoteOffers(isNull(), any(Path.class));
        assertFalse(summary.allOk());
        assertEquals(2, summary.failures().size());
        assertTrue(summary.failures().get(0).contains("aldi down"));
        assertEquals(2 + AggregatorClient.STORES.size() + 1 - 2, summary.results().size());
    }

    @Test
    void monthlyKeepsGoingWhenAStepThrowsOrBuildingAServiceFails() {
        stubAllOk();
        when(importer.importAldiRange()).thenThrow(new RuntimeException("range down"));
        RefreshService broken = new RefreshService(importer,
                () -> { throw new RuntimeException("Malformed config/store_overrides.json"); },
                () -> promoter, () -> discounts);

        RefreshSummary summary = broken.refreshMonthly();

        verify(promoter).promote();
        verify(discounts).promoteOffers(isNull(), any(Path.class));
        assertEquals(2, summary.failures().size());
        assertEquals(2, summary.results().size());
        assertFalse(summary.allOk());
    }

    @Test
    void aPartialStepMakesTheRunNotOk() {
        stubAllOk();
        when(promoter.promote()).thenReturn(new ImportSummary("all", "promote", "PARTIAL", 0, 0, new ArrayList<>(), "x"));

        RefreshSummary summary = refresh.refreshMonthly();

        assertTrue(summary.failures().isEmpty());
        assertFalse(summary.allOk());
    }
}
