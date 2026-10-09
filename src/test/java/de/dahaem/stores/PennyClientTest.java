package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PennyClientTest {

    /** Section tags shaped like penny.de/angebote: this week, next week, then the repeated menu copy. */
    private static final String PAGE =
            section("ab-montag--top-angebote", "top-angebote")
            + section("ab-donnerstag--obst-und-gemuese1", "obst-und-gemuese1")
            + section("ab-freitag--framstag", "framstag")
            + section("ab-naechsten-montag--top-angebote", "top-angebote")
            + section("ab-naechsten-donnerstag--weihnachten", "weihnachten")
            + section("ab-naechsten-freitag--framstag", "framstag")
            + section("ab-donnerstag--top-angebote", "top-angebote");   // later duplicate must not win

    private static String section(String id, String category) {
        return "<section\n      id=\"" + id + "\"\n      class=\"page-sector js-category-section\"\n"
                + "      data-category-type=\"food\"\n      data-period-category=\"false\"\n"
                + "      data-category-name=\"" + category + "\"\n      style=\"--category-offers-count: 3\"\n    >";
    }

    @Test
    void sectionIdsGiveEachCategoryItsStartDayPerWeek() {
        List<Map<String, DayOfWeek>> starts = PennyClient.categoryStarts(PAGE);

        assertEquals(Map.of("top-angebote", DayOfWeek.MONDAY,
                "obst-und-gemuese1", DayOfWeek.THURSDAY,
                "framstag", DayOfWeek.FRIDAY), starts.get(0));
        assertEquals(Map.of("top-angebote", DayOfWeek.MONDAY,
                "weihnachten", DayOfWeek.THURSDAY,
                "framstag", DayOfWeek.FRIDAY), starts.get(1));
    }

    @Test
    void pageWithoutDatedSectionsYieldsNoCategories() {
        List<Map<String, DayOfWeek>> starts = PennyClient.categoryStarts("<div data-category-name=\"getraenke\">");

        assertTrue(starts.get(0).isEmpty());
        assertTrue(starts.get(1).isEmpty());
    }

    @Test
    void weekStartIsTheIsoMonday() {
        assertEquals(LocalDate.of(2026, 10, 5), PennyClient.weekStart("2026-41"));
    }

    @Test
    void percentageTilesAreNotOffers() throws Exception {
        var tile = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"title\":\"Ferrero-Kühlartikel\",\"price\":\"20% Rabatt\",\"uuid\":\"u4\"}");

        assertNull(new PennyClient(null).toOffer(tile, "framstag", "15A-03-22",
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10)));
    }
}
