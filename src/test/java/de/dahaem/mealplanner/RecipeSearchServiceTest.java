package de.dahaem.mealplanner;

import de.dahaem.diet.DietFilterService;
import de.dahaem.diet.DietRules;
import de.dahaem.diet.DietStatus;
import de.dahaem.diet.DietVerdict;
import de.dahaem.shared.PageRequest;
import de.dahaem.tags.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The paging and diet logic of the recipe search with a mocked repository: no database. */
class RecipeSearchServiceTest {

    private RecipeRepository repository;
    private TagService tags;
    private DietFilterService diet;
    private RecipeSearchService service;

    @BeforeEach
    void setUp() {
        repository = mock(RecipeRepository.class);
        tags = mock(TagService.class);
        diet = mock(DietFilterService.class);
        service = new RecipeSearchService(repository, tags, diet);
        when(tags.tagNamesForRecipes(any())).thenReturn(Map.of());
    }

    private static Recipe recipe(int id) {
        return new Recipe(id, "Rezept " + id, 2, null, 10, 10, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static DietVerdict verdict(DietStatus status, boolean softHit) {
        DietVerdict.SoftHits soft = softHit
                ? new DietVerdict.SoftHits(List.of("Rind"), List.of(), List.of()) : null;
        return new DietVerdict(status, List.of(), soft);
    }

    private static DietRules hard() {
        return new DietRules(Set.of("Schwein"), Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    void withoutRulesTheDatabasePagesAndTheDietFilterIsNotTouched() {
        RecipeQuery query = RecipeQuery.none();
        when(repository.countMatching(query)).thenReturn(50L);
        when(repository.findPage(query, 10, 20)).thenReturn(List.of(recipe(21), recipe(22)));
        when(tags.tagNamesForRecipes(List.of(21, 22))).thenReturn(Map.of(21, List.of("vegan")));

        RecipeSearchService.Result result = service.search(query, DietRules.none(), new PageRequest(3, 10));

        assertEquals(50, result.total());
        assertEquals(5, result.totalPages());
        assertEquals(List.of(21, 22), result.items().stream().map(e -> e.recipe().getId()).toList());
        assertEquals(List.of("vegan"), result.items().get(0).tags());
        assertEquals(List.of(), result.items().get(1).tags());
        assertNull(result.items().get(0).diet());
        org.mockito.Mockito.verifyNoInteractions(diet);
    }

    @Test
    void aPagePastTheEndIsEmptyButKeepsTheTotalAndSkipsThePageQuery() {
        RecipeQuery query = RecipeQuery.none();
        when(repository.countMatching(query)).thenReturn(5L);

        RecipeSearchService.Result result = service.search(query, DietRules.none(), new PageRequest(2, 10));

        assertEquals(5, result.total());
        assertTrue(result.items().isEmpty());
        verify(repository, never()).findPage(any(), anyInt(), anyLong());
    }

    @Test
    void withRulesHiddenRecipesAreDroppedAndNotCounted() {
        RecipeQuery query = RecipeQuery.none();
        DietRules rules = hard();
        when(repository.findMatchingIds(query)).thenReturn(List.of(1, 2, 3, 4));
        Map<Integer, DietVerdict> verdicts = new HashMap<>();
        verdicts.put(1, verdict(DietStatus.OK, false));
        verdicts.put(2, verdict(DietStatus.HIDDEN, false));
        verdicts.put(3, verdict(DietStatus.UNCLEAR, false));
        verdicts.put(4, verdict(DietStatus.ANPASSBAR, false));
        when(diet.judgeAll(rules, List.of(1, 2, 3, 4))).thenReturn(verdicts);
        when(repository.findByIds(List.of(1, 4))).thenReturn(List.of(recipe(1), recipe(4)));

        RecipeSearchService.Result result = service.search(query, rules, new PageRequest(1, 10));

        assertEquals(2, result.total());   // UNCLEAR is left out without includeUnclear
        assertEquals(List.of(1, 4), result.items().stream().map(e -> e.recipe().getId()).toList());
        assertEquals(DietStatus.OK, result.items().get(0).diet().status());
        assertEquals(DietStatus.ANPASSBAR, result.items().get(1).diet().status());
        verify(repository, never()).findPage(any(), anyInt(), anyLong());
    }

    @Test
    void displayOrderIsSoftHitsLastThenStatusThenTheRequestedSortAsTieBreak() {
        DietRules rules = new DietRules(Set.of("Schwein"), Set.of(), Set.of(), Set.of("Rind"), Set.of(), true);
        Map<Integer, DietVerdict> verdicts = new HashMap<>();
        verdicts.put(10, verdict(DietStatus.UNCLEAR, false));
        verdicts.put(11, verdict(DietStatus.OK, true));
        verdicts.put(12, verdict(DietStatus.OK, false));
        verdicts.put(13, verdict(DietStatus.ANPASSBAR, false));
        verdicts.put(14, verdict(DietStatus.OK, false));
        verdicts.put(15, verdict(DietStatus.HIDDEN, false));

        // 10..15 is the order the database returned for the requested sort
        List<Integer> shown = RecipeSearchService.visibleInDisplayOrder(List.of(10, 11, 12, 13, 14, 15), verdicts, rules);

        assertEquals(List.of(12, 14, 13, 10, 11), shown);
    }

    @Test
    void pagesCutTheFilteredListWithoutGapsOrRepeats() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            ids.add(i);
        }
        List<Integer> seen = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            seen.addAll(RecipeSearchService.slice(ids, new PageRequest(page, 10)));
        }
        assertEquals(ids, seen);
        assertEquals(5, RecipeSearchService.slice(ids, new PageRequest(3, 10)).size());
        assertTrue(RecipeSearchService.slice(ids, new PageRequest(4, 10)).isEmpty());
        assertTrue(RecipeSearchService.slice(List.of(), new PageRequest(1, 10)).isEmpty());
    }

    @Test
    void withRulesAndNoCandidatesNothingIsJudgedOrLoaded() {
        RecipeQuery query = RecipeQuery.none();
        when(repository.findMatchingIds(query)).thenReturn(List.of());

        RecipeSearchService.Result result = service.search(query, hard(), new PageRequest(1, 10));

        assertEquals(0, result.total());
        assertEquals(0, result.totalPages());
        assertTrue(result.items().isEmpty());
        verify(diet, never()).judgeAll(any(), any());
    }
}
