package de.dahaem.stores;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits the "Sorten:" block of an ALDI product description into flavour/size variants.
 *
 *   "Sorten:\n\n * Paprika-Kräuter, 125 g\n * Natur, 150 g"
 *     -> [Paprika-Kräuter / 125 g, Natur / 150 g]
 *
 * Bullets without a size keep size null. No "Sorten:" block -> empty list.
 */
final class VariantParser {

    private static final Pattern HEADER = Pattern.compile("^\\**\\s*Sorten?\\s*:", Pattern.CASE_INSENSITIVE);
    // "125 g", "1,5 kg", "6 x 0,33 l", "10 Stück"
    private static final Pattern SIZE = Pattern.compile(
            "^\\d+(?:[.,]\\d+)?\\s*(?:x\\s*\\d+(?:[.,]\\d+)?\\s*)?(?:g|kg|ml|cl|l|Stück|St\\.)(?![A-Za-zÄÖÜäöüß])",
            Pattern.CASE_INSENSITIVE);

    private VariantParser() {
    }

    static List<ProductVariant> parse(String description) {
        List<ProductVariant> variants = new ArrayList<>();
        if (description == null) {
            return variants;
        }
        boolean inBlock = false;
        for (String raw : description.split("\\R")) {
            String line = raw.strip();
            if (!inBlock) {
                inBlock = HEADER.matcher(line).find();
                continue;
            }
            if (line.isEmpty()) {
                continue;
            }
            char first = line.charAt(0);
            if (first != '*' && first != '-' && first != '•') {
                break;                                   // block ended
            }
            String item = line.replaceFirst("^[*\\-•\\s]+", "").strip();
            String name = item;
            String size = null;
            int comma = item.lastIndexOf(',');
            if (comma > 0) {
                String tail = item.substring(comma + 1).strip();
                if (SIZE.matcher(tail).find()) {
                    name = item.substring(0, comma).strip();
                    size = tail;
                }
            }
            variants.add(new ProductVariant(name, size));
        }
        return variants;
    }
}
