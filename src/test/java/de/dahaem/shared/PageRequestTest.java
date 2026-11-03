package de.dahaem.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRequestTest {

    @Test
    void noParametersGiveTheFirstPageOfTheDefaultSize() {
        PageRequest request = PageRequest.of(null, null);
        assertEquals(1, request.page());
        assertEquals(24, request.pageSize());
        assertEquals(0, request.offset());
    }

    @Test
    void offsetCountsFromPageOne() {
        assertEquals(0, new PageRequest(1, 10).offset());
        assertEquals(10, new PageRequest(2, 10).offset());
        assertEquals(2_000_000L * 100 - 100, new PageRequest(2_000_000, 100).offset());   // no int overflow
    }

    @Test
    void totalPagesRoundsUp() {
        PageRequest request = new PageRequest(1, 10);
        assertEquals(0, request.totalPages(0));
        assertEquals(1, request.totalPages(1));
        assertEquals(1, request.totalPages(10));
        assertEquals(2, request.totalPages(11));
        assertEquals(1100, request.totalPages(11_000));
    }

    @Test
    void outOfRangeValuesAreRejectedNotClamped() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, null));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(-1, null));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(null, 0));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(null, 101));
        assertEquals(100, PageRequest.of(null, 100).pageSize());
    }

    @Test
    void likePatternEscapesWildcardsSoTheyAreSearchedLiterally() {
        assertEquals("%Käse%", LikePattern.contains("Käse"));
        assertEquals("%100\\% \\_x\\\\y%", LikePattern.contains("100% _x\\y"));
    }
}
