package de.dahaem.barcode;

import java.io.IOException;
import java.util.Optional;

/** Where a barcode DAHÄM does not know can be looked up (an open data source). Only the number is ever sent. */
public interface ProductDataSource {

    /** False when the outside lookup is switched off. */
    boolean enabled();

    /** The product for a normalised barcode, empty if the source does not know it; an IOException if it cannot be reached. */
    Optional<ProductSuggestion> find(String code) throws IOException;

    /** The switched-off source: nothing leaves the server. */
    static ProductDataSource disabled() {
        return new ProductDataSource() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public Optional<ProductSuggestion> find(String code) {
                return Optional.empty();
            }
        };
    }
}
