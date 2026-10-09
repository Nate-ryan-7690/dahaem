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

    private static StoreOffer offerOf(String tileJson) throws Exception {
        var tile = new com.fasterxml.jackson.databind.ObjectMapper().readTree(tileJson);
        return new PennyClient(null).toOffer(tile, "top-angebote", "15A-03-22",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10));
    }

    @Test
    void strikethroughOriginalPriceIsTheNormalPrice() throws Exception {
        StoreOffer offer = offerOf("{\"title\":\"Feldsalat*\",\"price\":\"0.99\",\"originalPrice\":\"1.29\","
                + "\"originalPriceType\":\"striketrough\",\"uuid\":\"u1\"}");

        assertEquals(1.29, offer.normalPrice());
        assertEquals(0.99, offer.salePrice());
    }

    @Test
    void uvpOriginalPriceIsNotAPennyPrice() throws Exception {
        StoreOffer offer = offerOf("{\"title\":\"MILKA Schokolade*\",\"price\":\"2.79\",\"originalPrice\":\"4.99\","
                + "\"originalPriceType\":\"uvp\",\"uuid\":\"u2\"}");

        assertNull(offer.normalPrice());
        assertEquals(2.79, offer.salePrice());
    }

    @Test
    void lowestPrice30DaysIsPreferredAsReferencePrice() throws Exception {
        StoreOffer offer = offerOf("{\"title\":\"Feldsalat*\",\"price\":\"0.99\",\"originalPrice\":\"1.29\","
                + "\"originalPriceType\":\"striketrough\",\"lowestPrice30Days\":\"1.19\",\"uuid\":\"u3\"}");

        assertEquals(1.19, offer.normalPrice());
    }
}
