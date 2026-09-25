package de.dahaem.stores;

import java.util.List;

/**
 * One product from a chain's full range, as pulled from its website.
 * Not matched to an inventory_item yet; maps to a row in store_product.
 *
 * regularPrice is the normal shelf price. promoPriceOnly is true when the site
 * showed only a promotion price for this product (no crossed-out normal price),
 * so regularPrice can't be trusted as the normal price on this pull.
 * basePrice/baseUnit is the price per kg / Liter the store prints on the label.
 * deposit is the Pfand per unit (null = none); it belongs to the product, not to an offer.
 */
public record StoreProduct(
        String externalId,
        String brand,
        String name,
        String salesUnit,
        Double regularPrice,
        boolean promoPriceOnly,
        Double basePrice,
        String baseUnit,
        Double deposit,
        String category,
        String description,
        String url,
        boolean available,
        List<ProductVariant> variants) {

    /** Same product with a different regular price (records are immutable, so we build a copy). */
    public StoreProduct withRegularPrice(Double price) {
        return new StoreProduct(externalId, brand, name, salesUnit, price, promoPriceOnly,
                basePrice, baseUnit, deposit, category, description, url, available, variants);
    }
}
