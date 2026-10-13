package de.dahaem.receipts;

import de.dahaem.stores.CatalogItem;
import de.dahaem.stores.StoreRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Imports PENNY receipt texts as purchase history into the receipt tables (migration 0008).
 * A dry run (the default) only reads: the catalog for matching and the receipt keys for
 * duplicates. With {@code commit}, every new receipt -- header, every line, every adjustment --
 * is written in ONE transaction ({@link ReceiptRepository#saveReceipts}); any error rolls all of it back.
 *
 * <p>A historical import never changes stock: it does not call PurchaseService and writes neither
 * purchase_history nor inventory_item. The household's stock is built from new purchases and
 * check-ins, not reconstructed from old receipts.
 *
 * <p>A receipt is skipped (and reported) if it is not a PENNY receipt, lacks its date, receipt
 * number or total, or does not balance to the cent; one already stored (same store, date-time and
 * receipt number) is a duplicate and is not written again.
 */
public class ReceiptImportService {

    /** The existing {@code store} row for PENNY (same name the offer import uses). */
    static final String STORE_NAME = "Penny";

    public record ReceiptFile(String name, String text) {
    }

    /** One product line as it will be stored. */
    public record PlannedLine(int lineNo, ReceiptLine line, String brand, BigDecimal paidTotal, ReceiptMatcher.Match match) {
    }

    public enum Status { NEW, DUPLICATE, SKIPPED }

    /** One receipt's outcome; {@code receipt} and {@code lines} are null/empty when it could not be read. */
    public record ReceiptResult(String file, Status status, String reason, ParsedReceipt receipt, List<PlannedLine> lines,
                                BigDecimal storedTotal) {
    }

    private final ReceiptRepository repository;
    private final Supplier<List<CatalogItem>> catalog;
    private final ReceiptMatcher.Overrides overrides;
    private final ReceiptParser parser = new ReceiptParser();

    public ReceiptImportService(ReceiptRepository repository, Supplier<List<CatalogItem>> catalog,
                                ReceiptMatcher.Overrides overrides) {
        this.repository = repository;
        this.catalog = catalog;
        this.overrides = overrides;
    }

    public ReceiptImportService(ReceiptMatcher.Overrides overrides) {
        this(new ReceiptRepository(), new StoreRepository()::loadCatalog, overrides);
    }

    public ReceiptImportReport run(List<ReceiptFile> files, boolean commit) {
        List<CatalogItem> items = catalog.get();
        checkOverrideIds(items);
        ReceiptMatcher matcher = new ReceiptMatcher(overrides, items);

        Integer storeId = repository.findStoreId(STORE_NAME);
        List<ReceiptResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ReceiptFile f : files) {
            ReceiptResult r = plan(f, matcher);
            if (r.status() == Status.NEW) {
                ParsedReceipt p = r.receipt();
                boolean stored = storeId != null && repository.exists(storeId, p.purchasedAt(), p.receiptNumber());
                if (!seen.add(p.purchasedAt() + "#" + p.receiptNumber()) || stored) {
                    r = new ReceiptResult(f.name(), Status.DUPLICATE,
                            stored ? "already stored" : "same receipt twice in this run", p, r.lines(), r.storedTotal());
                }
            }
            results.add(r);
        }
        List<ReceiptResult> toWrite = results.stream().filter(r -> r.status() == Status.NEW).toList();
        if (commit && !toWrite.isEmpty()) {
            repository.saveReceipts(STORE_NAME, toWrite);         // one transaction
        }
        return new ReceiptImportReport(commit, results);
    }

    /** Parses one file and decides its lines and matches; no database access. */
    ReceiptResult plan(ReceiptFile f, ReceiptMatcher matcher) {
        StoreFormat format = parser.detectFormat(f.text());
        if (format != StoreFormat.PENNY) {
            return skipped(f, "not a PENNY receipt (" + format + ")", null);
        }
        ParsedReceipt r = parser.parse(f.text(), format);
        List<String> missing = new ArrayList<>();
        if (r.purchasedAt() == null) {
            missing.add("date");
        }
        if (r.receiptNumber() == null) {
            missing.add("receipt number");
        }
        if (r.total() == null) {
            missing.add("total");
        }
        if (!missing.isEmpty()) {
            return skipped(f, "no " + String.join(", ", missing), r);
        }
        List<PlannedLine> lines = new ArrayList<>();
        for (int i = 0; i < r.lines().size(); i++) {
            ReceiptLine line = r.lines().get(i);
            lines.add(new PlannedLine(i + 1, line, ReceiptNames.brand(line.rawName()), r.paidTotal(i),
                    matcher.match(line.rawName())));
        }
        BigDecimal stored = storedTotal(r, lines);
        if (stored.compareTo(r.total()) != 0) {
            return new ReceiptResult(f.name(), Status.SKIPPED, "does not balance: lines and adjustments give " + stored
                    + ", printed " + r.total(), r, lines, stored);
        }
        return new ReceiptResult(f.name(), Status.NEW, null, r, lines, stored);
    }

    /** Line paid totals plus every adjustment not already inside one of them (deposits, returns, unattached discounts). */
    static BigDecimal storedTotal(ParsedReceipt r, List<PlannedLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PlannedLine l : lines) {
            sum = sum.add(l.paidTotal());
        }
        for (ReceiptAdjustment a : r.adjustments()) {
            if (!inPaidTotal(a)) {
                sum = sum.add(a.amount());
            }
        }
        return sum;
    }

    private static boolean inPaidTotal(ReceiptAdjustment a) {
        return a.type().isDiscount() && a.lineIndex() != null;
    }

    /** An override pointing at an id the catalog doesn't have would fail the whole commit; refuse up front. */
    private void checkOverrideIds(List<CatalogItem> items) {
        Set<Integer> ids = items.stream().map(CatalogItem::id).collect(Collectors.toSet());
        Set<String> bad = new TreeSet<>();
        for (Map.Entry<String, Integer> e : overrides.exact().entrySet()) {
            if (e.getValue() > 0 && !ids.contains(e.getValue())) {
                bad.add(e.getKey() + " -> " + e.getValue());
            }
        }
        if (!bad.isEmpty()) {
            throw new IllegalStateException("receipt overrides point at items that are not in inventory_item: " + bad);
        }
    }

    private static ReceiptResult skipped(ReceiptFile f, String reason, ParsedReceipt r) {
        return new ReceiptResult(f.name(), Status.SKIPPED, reason, r, List.of(), null);
    }
}
