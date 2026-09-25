package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AggregatorClientTest {

    private final AggregatorClient client = new AggregatorClient(null);

    /**
     * Three inline produkt_box tiles, shaped like the real aktionspreis.de markup
     * described in AggregatorClient's class doc:
     *   1) a full tile: discount %, variant subtitle, base price, TIEFSTPREIS badge
     *   2) a plain tile: no discount, no variant, no base price
     *   3) an incomplete tile with a name but no sale price, which must be skipped
     */
    private static final String HTML =
            "<a href=\"/angebote/arla-buko-12345\" title=\"Buko (200g)\">"
            + "<div class=\"produkt_box\">"
            + "<span class=\"percent_new\"> 55 %</span>"
            + "<b>Arla Buko<br> versch. Sorten</b>"
            + "<b style=\"color:#c03938;\"> 0,89 €</b>"
            + "<span>4,45 € je kg</span>"
            + "<span>TIEFSTPREIS</span>"
            + "</div></a>"
            + "<a href=\"/angebote/milch-frisch\" title=\"Frische Milch (1l)\">"
            + "<div class=\"produkt_box\">"
            + "<b>Landliebe Frischmilch</b>"
            + "<b style=\"color:#c03938;\">1,29 €</b>"
            + "</div></a>"
            + "<a href=\"/angebote/incomplete-produkt\" title=\"Foo (1x)\">"
            + "<div class=\"produkt_box\">"
            + "<b>Foo Ohne Preis</b>"
            + "</div></a>";

    @Test
    void parsesFullTileWithDiscountBasePriceAndBadge() {
        List<StoreOffer> offers = client.parse(HTML);
        StoreOffer o = offers.get(0);

        assertEquals("arla-buko-12345", o.externalId());
        assertEquals("Arla Buko", o.name());
        assertEquals("200g", o.salesUnit());
        assertEquals(0.89, o.salePrice(), 1e-9);
        assertEquals(4.45, o.basePrice(), 1e-9);
        assertEquals("kg", o.baseUnit());
        assertEquals(1.98, o.normalPrice(), 1e-9); // 0.89 / (1 - 0.55), rounded to cents
        assertEquals("-55% TIEFSTPREIS versch. Sorten", o.promoText());
    }

    @Test
    void parsesPlainTileWithoutDiscountOrBasePrice() {
        List<StoreOffer> offers = client.parse(HTML);
        StoreOffer o = offers.get(1);

        assertEquals("milch-frisch", o.externalId());
        assertEquals("Landliebe Frischmilch", o.name());
        assertEquals("1l", o.salesUnit());
        assertEquals(1.29, o.salePrice(), 1e-9);
        assertNull(o.basePrice());
        assertNull(o.baseUnit());
        assertNull(o.normalPrice());
        assertNull(o.promoText());
    }

    @Test
    void tileWithoutSalePriceIsSkipped() {
        List<StoreOffer> offers = client.parse(HTML);
        assertEquals(2, offers.size());
        assertNull(offers.stream().filter(o -> "incomplete-produkt".equals(o.externalId())).findAny().orElse(null));
    }

    @Test
    void emptyHtmlYieldsNoOffers() {
        assertEquals(0, client.parse("no tiles here").size());
    }
}
