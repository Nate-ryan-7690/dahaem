package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchGateTest {

    private static final int KARTOFFEL = 31;
    private static final int ZITRONE = 186;
    private static final int KAFFEE = 1692;

    /** Potatoes: nets and loose packs at roughly 0.80 to 1.50 EUR/kg in the range data; coffee at roughly 12 EUR/kg. */
    private static MatchGate.References references() {
        MatchGate.References refs = new MatchGate.References();
        refs.add(KARTOFFEL, new StorePromotionService.Pack(2000, "g"), 1.99);
        refs.add(KARTOFFEL, new StorePromotionService.Pack(2500, "g"), 2.49);
        refs.add(KARTOFFEL, new StorePromotionService.Pack(1000, "g"), 1.29);
        refs.add(KARTOFFEL, new StorePromotionService.Pack(1500, "g"), 1.49);
        refs.add(KARTOFFEL, new StorePromotionService.Pack(1000, "g"), 1.49);
        for (double price : new double[]{5.99, 6.49, 5.49}) {
            refs.add(KAFFEE, new StorePromotionService.Pack(500, "g"), price);
        }
        return refs;
    }

    @Test
    void lemonSoftDrinkIsRejectedForTheZitroneItem() {
        MatchGate.Verdict v = MatchGate.check("g", StorePromotionService.parseSize("0,75-L-Flasche"), 0.49,
                ZITRONE, references());

        assertFalse(v.ok());
        assertTrue(v.reason().startsWith("unit"), v.reason());
    }

    @Test
    void marzipankartoffelnAreRejectedForTheKartoffelItemOnPrice() {
        MatchGate.Verdict v = MatchGate.check("g", StorePromotionService.parseSize("125 g"), 0.99,
                KARTOFFEL, references());

        assertFalse(v.ok());
        assertTrue(v.reason().startsWith("price"), v.reason());
    }

    @Test
    void speisekartoffeln5kgAreAcceptedForTheKartoffelItem() {
        MatchGate.Verdict v = MatchGate.check("g", StorePromotionService.parseSize("5-kg-Netz"), 2.99,
                KARTOFFEL, references());

        assertTrue(v.ok(), v.reason());
    }

    @Test
    void pieceCountPackIsRejectedForAWeightItem() {
        assertFalse(MatchGate.check("g", StorePromotionService.parseSize("15 Stück"), 1.99, 484, references()).ok());
    }

    @Test
    void containerUnitItemAcceptsAnyPackFamily() {
        assertTrue(MatchGate.check("Packung", StorePromotionService.parseSize("500 g"), 5.99, KAFFEE, references()).ok());
        assertTrue(MatchGate.check("Flasche", StorePromotionService.parseSize("1 l"), 1.78, 152, references()).ok());
    }

    @Test
    void tooFewReferencesMeansNoPriceJudgement() {
        assertTrue(MatchGate.check("g", StorePromotionService.parseSize("100 g"), 99.0, 999, references()).ok());
    }

    @Test
    void anOverrideSkipsThePriceCheckButNotTheUnitCheck() {
        StorePromotionService.Pack marzipan = StorePromotionService.parseSize("125 g");
        assertFalse(MatchGate.check("g", marzipan, 0.99, KARTOFFEL, references()).ok(), "price outlier is held");
        assertTrue(MatchGate.check("g", marzipan, 0.99, KARTOFFEL, references(), true).ok(), "override: price not judged");
        assertFalse(MatchGate.check("g", StorePromotionService.parseSize("0,75-L-Flasche"), 0.49, ZITRONE,
                references(), true).ok(), "override: the unit still has to fit");
    }

    @Test
    void washLoadsFormAFamilyOfTheirOwn() {
        assertTrue(MatchGate.check("WL", StorePromotionService.parseSize("46-50 WL"), 6.99, 1669, references()).ok());
        assertFalse(MatchGate.check("WL", StorePromotionService.parseSize("4,95 kg"), 6.99, 1669, references()).ok());
    }
}
