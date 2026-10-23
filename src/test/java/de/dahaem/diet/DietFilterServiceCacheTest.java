package de.dahaem.diet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The snapshot cache of DietFilterService, with a fake clock and a mocked repository: no database. */
class DietFilterServiceCacheTest {

    private static final long SECOND = 1_000_000_000L;

    private DietFilterRepository repository;
    private AtomicLong clock;
    private DietFilterService service;
    private DietRules rules;

    @BeforeEach
    void setUp() {
        repository = mock(DietFilterRepository.class);
        when(repository.loadAll()).thenReturn(Map.of(
                1, new RecipeDietFacts(1, Set.of("Schwein"), List.of(), List.of())));
        when(repository.tagGroups()).thenReturn(Map.of("Schwein", "Tier"));
        when(repository.dataGeneration()).thenReturn(1L);
        clock = new AtomicLong(1000 * SECOND);
        service = new DietFilterService(repository, DietPresets.load(DietPresets.DEFAULT_FILE), 60, clock::get);
        rules = service.rules(null, List.of("Schwein"), null, null, null, false);
    }

    @Test
    void severalRequestsWithinTheTtlLoadTheFactsOnce() {
        assertEquals(DietStatus.HIDDEN, service.judgeAll(rules, List.of(1)).get(1).status());
        clock.addAndGet(59 * SECOND);
        service.judgeAll(rules, List.of(1));
        service.judgeAll(rules, List.of(1));

        verify(repository, times(1)).loadAll();
    }

    @Test
    void theFactsAreReloadedAfterTheTtl() {
        service.judgeAll(rules, List.of(1));
        clock.addAndGet(60 * SECOND);
        service.judgeAll(rules, List.of(1));

        verify(repository, times(2)).loadAll();
    }

    @Test
    void invalidateForcesAReload() {
        service.judgeAll(rules, List.of(1));
        service.invalidate();
        service.judgeAll(rules, List.of(1));

        verify(repository, times(2)).loadAll();
    }

    @Test
    void aReplacedDatabaseDropsTheSnapshot() {
        service.judgeAll(rules, List.of(1));
        when(repository.dataGeneration()).thenReturn(2L);
        service.judgeAll(rules, List.of(1));

        verify(repository, times(2)).loadAll();
    }

    @Test
    void zeroSecondsMeansNoCaching() {
        DietFilterService uncached = new DietFilterService(repository, DietPresets.load(DietPresets.DEFAULT_FILE), 0, clock::get);
        uncached.judgeAll(rules, List.of(1));
        uncached.judgeAll(rules, List.of(1));

        verify(repository, times(2)).loadAll();
    }

    @Test
    void judgingOneRecipeNeverUsesTheSnapshot() {
        service.judgeAll(rules, List.of(1));
        when(repository.loadRecipe(1)).thenReturn(new RecipeDietFacts(1, Set.of(), List.of(), List.of()));

        service.judge(rules, 1);

        verify(repository, times(1)).loadAll();
        verify(repository, times(1)).loadRecipe(1);
    }
}
