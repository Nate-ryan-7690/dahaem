package de.dahaem.receipts;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one receipt import did (or, in a dry run, would do): receipts new / duplicate / skipped,
 * how the lines of the new receipts matched, the most frequent unmatched names, and per receipt
 * the balance check (stored line paid totals + other adjustments = printed total).
 */
public record ReceiptImportReport(boolean committed, List<ReceiptImportService.ReceiptResult> receipts) {

    private static final int TOP_UNMATCHED = 40;

    public ReceiptImportReport {
        receipts = List.copyOf(receipts);
    }

    public long count(ReceiptImportService.Status status) {
        return receipts.stream().filter(r -> r.status() == status).count();
    }

    /** Lines of the NEW receipts (what is or would be written). */
    public List<ReceiptImportService.PlannedLine> newLines() {
        return receipts.stream().filter(r -> r.status() == ReceiptImportService.Status.NEW)
                .flatMap(r -> r.lines().stream()).toList();
    }

    public long linesBy(String source) {
        return newLines().stream().filter(l -> source.equals(l.match().source())).count();
    }

    public long excludedLines() {
        return newLines().stream().filter(l -> l.match().excluded()).count();
    }

    public long unmatchedLines() {
        return newLines().stream().filter(l -> !l.match().matched() && !l.match().excluded()).count();
    }

    /** Unmatched receipt key -> {count, an example name as printed}, most frequent first. */
    public Map<String, Object[]> unmatchedNames() {
        Map<String, Object[]> byKey = new LinkedHashMap<>();
        for (ReceiptImportService.PlannedLine l : newLines()) {
            if (!l.match().matched() && !l.match().excluded()) {
                byKey.computeIfAbsent(l.match().key(), k -> new Object[] {0, l.line().rawName()});
                byKey.get(l.match().key())[0] = (int) byKey.get(l.match().key())[0] + 1;
            }
        }
        Map<String, Object[]> sorted = new LinkedHashMap<>();
        byKey.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Object[]> e) -> -(int) e.getValue()[0])
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** Sum of the printed totals of the NEW receipts. */
    public BigDecimal newTotal() {
        return receipts.stream().filter(r -> r.status() == ReceiptImportService.Status.NEW)
                .map(r -> r.receipt().total()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean allBalanced() {
        return count(ReceiptImportService.Status.SKIPPED) == 0;
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(committed ? "Receipt import -- COMMITTED (one transaction)\n"
                : "Receipt import -- DRY RUN, nothing written (add --commit to write)\n");
        sb.append(String.format("receipts: %d new, %d duplicate, %d skipped%n", count(ReceiptImportService.Status.NEW),
                count(ReceiptImportService.Status.DUPLICATE), count(ReceiptImportService.Status.SKIPPED)));
        sb.append("\nper receipt (stored line paid totals + other adjustments = printed total):\n");
        for (ReceiptImportService.ReceiptResult r : receipts) {
            ParsedReceipt p = r.receipt();
            String head = p == null ? "" : String.format("%s  #%s", p.purchasedAt(), p.receiptNumber());
            sb.append(String.format("  %-9s %-28s %s", r.status(), head, r.file()));
            if (r.storedTotal() != null) {
                BigDecimal paid = r.lines().stream().map(ReceiptImportService.PlannedLine::paidTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                sb.append(String.format("  %d lines  %s + %s = %s  printed %s  %s", r.lines().size(), paid,
                        r.storedTotal().subtract(paid), r.storedTotal(), p.total(),
                        r.storedTotal().compareTo(p.total()) == 0 ? "OK" : "MISMATCH"));
            }
            if (r.reason() != null) {
                sb.append("  (").append(r.reason()).append(')');
            }
            sb.append('\n');
            if (p != null && r.status() == ReceiptImportService.Status.NEW) {
                p.unparsedLines().forEach(u -> sb.append("            unparsed: ").append(u).append('\n'));
            }
        }
        List<ReceiptImportService.PlannedLine> lines = newLines();
        sb.append(String.format("%nlines of new receipts: %d -- matched by override %d, by rule %d; excluded %d; unmatched %d%n",
                lines.size(), linesBy(ReceiptMatcher.OVERRIDE), linesBy(ReceiptMatcher.RULE), excludedLines(),
                unmatchedLines()));
        Map<String, Object[]> unmatched = unmatchedNames();
        if (!unmatched.isEmpty()) {
            sb.append(String.format("top unmatched names (%d distinct; count, key for receipt_overrides.json, as printed):%n",
                    unmatched.size()));
            unmatched.entrySet().stream().limit(TOP_UNMATCHED).forEach(e -> sb.append(String.format("  %3d  %-32s %s%n",
                    (int) e.getValue()[0], e.getKey(), e.getValue()[1])));
        }
        sb.append(String.format("%noverall total of new receipts: EUR %s%s%n", newTotal(),
                allBalanced() ? "" : "  -- some receipts were skipped, see above"));
        return sb.toString();
    }
}
