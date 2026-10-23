package de.dahaem.stores;

/**
 * Which region an offer pull is for. Today it comes from config/stores.properties; later it will be
 * built per (chain, price region) from the households' locations.
 *
 * marketId is the Penny store ("wwIdent"), postalCode the PLZ that regional aggregator chains
 * (famila, Edeka, Rewe) are gated behind. A source ignores the parts it does not need.
 */
public record OfferScope(String marketId, String postalCode) {
}
