package de.dahaem.receipts;

import de.dahaem.stores.CatalogItem;
import de.dahaem.stores.StoreRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Re-runs the receipt matcher (the current config/receipt_overrides.json plus the name rules) on receipt
 * lines that are already stored, so names can be mapped after the import without importing again.
 *
 * <p>Only lines whose match_source is NULL, 'rule' or 'override' are looked at; a 'manual' line is never
 * changed. A line changes when its item, match source or brand differs from what the matcher gives now
 * (an override moved to {@code review} clears a former match). A dry run (the default) only reads; with
 * {@code commit} all changes are written in ONE transaction. Receipts, adjustments, inventory_item stock
 * and purchase_history are never touched. Override ids missing from inventory_item are refused, as in
 * the import.
 */
public class ReceiptRematchService {

    /** match_source of a line mapped by hand; the rematch leaves it alone. */
    public static final String MANUAL = "manual";

    /** One stored line whose match changes. */
    public record Change(ReceiptRepository.StoredLine line, Integer newItemId, String newSource, String newBrand) {
    }

    private final ReceiptRepository repository;
    private final Supplier<List<CatalogItem>> catalog;
    private final ReceiptMatcher.Overrides overrides;

    public ReceiptRematchService(ReceiptRepository repository, Supplier<List<CatalogItem>> catalog,
                                 ReceiptMatcher.Overrides overrides) {
        this.repository = repository;
        this.catalog = catalog;
        this.overrides = overrides;
    }

    public ReceiptRematchService(ReceiptMatcher.Overrides overrides) {
        this(new ReceiptRepository(), new StoreRepository()::loadCatalog, overrides);
    }

    public ReceiptRematchReport run(boolean commit) {
        List<CatalogItem> items = catalog.get();
        ReceiptImportService.checkOverrideIds(overrides, items);
        ReceiptMatcher matcher = new ReceiptMatcher(overrides, items);

        List<ReceiptRepository.StoredLine> lines = repository.findRematchCandidates();
        List<Change> changes = new ArrayList<>();
        for (ReceiptRepository.StoredLine l : lines) {
            ReceiptMatcher.Match m = matcher.match(l.rawName());
            String brand = ReceiptNames.brand(l.rawName());
            if (!Objects.equals(m.itemId(), l.itemId()) || !Objects.equals(m.source(), l.matchSource())
                    || !Objects.equals(brand, l.brand())) {
                changes.add(new Change(l, m.itemId(), m.source(), brand));
            }
        }
        if (commit && !changes.isEmpty()) {
            repository.updateMatches(changes);                // one transaction
        }
        Map<Integer, String> names = items.stream()
                .collect(Collectors.toMap(CatalogItem::id, CatalogItem::deutsch, (a, b) -> a));
        return new ReceiptRematchReport(commit, lines.size(), changes, names::get);
    }

    /** What a rematch changed (or, in a dry run, would change). */
    public record ReceiptRematchReport(boolean committed, int linesChecked, List<Change> changes,
                                       Function<Integer, String> itemName) {

        public ReceiptRematchReport {
            changes = List.copyOf(changes);
        }

        /** Changed lines that end up with an item. */
        public long nowMatched() {
            return changes.stream().filter(c -> c.newItemId() != null).count();
        }

        /** Changed lines that end up without an item (moved to review, or excluded with 0). */
        public long nowUnmatched() {
            return changes.stream().filter(c -> c.newItemId() == null).count();
        }

        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append(committed ? "Receipt rematch -- COMMITTED (one transaction)\n"
                    : "Receipt rematch -- DRY RUN, nothing written (add --commit to write)\n");
            sb.append(String.format("lines checked (match_source NULL / rule / override; 'manual' is never touched): %d%n",
                    linesChecked));
            sb.append(String.format("lines changed: %d -- now matched %d, now unmatched %d%n",
                    changes.size(), nowMatched(), nowUnmatched()));
            for (Change c : changes) {
                ReceiptRepository.StoredLine l = c.line();
                sb.append(String.format("  %s #%s line %d  %-24s %s -> %s", l.purchasedAt(), l.receiptNumber(), l.lineNo(),
                        l.rawName(), describe(l.itemId(), l.matchSource()), describe(c.newItemId(), c.newSource())));
                if (!Objects.equals(l.brand(), c.newBrand())) {
                    sb.append(String.format("  brand %s -> %s", l.brand(), c.newBrand()));
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        private String describe(Integer itemId, String source) {
            if (itemId == null) {
                return "unmatched";
            }
            String name = itemName.apply(itemId);
            return itemId + (name == null ? "" : " " + name) + " (" + source + ")";
        }
    }
}
