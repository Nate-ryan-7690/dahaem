package de.dahaem.stores;

import java.time.LocalDate;

/**
 * Row builders for the review reports (unmatched_offers.tsv, review_matches.tsv, review_prices.tsv).
 * Every row carries what is needed to find the offer in the flyer again: store name, source, external id,
 * the flyer page it was read from, pack size, price and validity. The original leading columns stay where they were.
 */
final class ReviewReports {

    static final String EXTRA = "store\tsource\texternal_id\tflyer_url\tsales_unit\tprice\tvalid_from\tvalid_until";

    private ReviewReports() {
    }

    /** The flyer page an offer was read from; empty when the source is unknown. */
    static String flyerUrl(OfferRow o, LocalDate today) {
        boolean next = o.validFrom() != null && LocalDate.parse(o.validFrom()).isAfter(today);
        String url;
        if ("AGGREGATOR".equals(o.source())) {
            url = AggregatorClient.flyerUrl(o.storeName(), next);
        } else if ("Aldi".equalsIgnoreCase(o.storeName())) {
            url = AldiNordClient.flyerUrl(next);
        } else if ("Penny".equalsIgnoreCase(o.storeName())) {
            url = PennyClient.flyerUrl();
        } else {
            url = null;
        }
        return url == null ? "" : url;
    }

    private static String tail(OfferRow o, LocalDate today) {
        return o.storeName() + "\t" + o.source() + "\t" + o.externalId() + "\t" + flyerUrl(o, today) + "\t"
                + nz(o.salesUnit()) + "\t" + o.salePrice() + "\t" + o.validFrom() + "\t" + o.validUntil();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** unmatched_offers.tsv: store_id, brand, name, then the identification columns. */
    static String unmatched(OfferRow o, LocalDate today) {
        return o.storeId() + "\t" + o.brand() + "\t" + o.name() + "\t" + tail(o, today);
    }

    /** review_matches.tsv: store_id, external_id, offer, sales_unit, sale_price, proposed_item, reason, then store..valid_until. */
    static String held(OfferRow o, String proposedItem, String reason, LocalDate today) {
        return o.storeId() + "\t" + o.externalId() + "\t" + o.name() + "\t" + nz(o.salesUnit()) + "\t"
                + o.salePrice() + "\t" + proposedItem + "\t" + reason + "\t" + tail(o, today);
    }

    /** review_prices.tsv: store_id, external_id, offer, sales_unit, sale_price, then store..valid_until. */
    static String badPrice(OfferRow o, LocalDate today) {
        return o.storeId() + "\t" + o.externalId() + "\t" + o.name() + "\t" + nz(o.salesUnit()) + "\t"
                + o.salePrice() + "\t" + tail(o, today);
    }
}
