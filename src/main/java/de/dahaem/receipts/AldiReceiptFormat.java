package de.dahaem.receipts;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.dahaem.receipts.ReceiptText.MONEY;

/**
 * ALDI layout. Item lines end in a tax group digit ("NAME  7,99 € 1"); weight, multi-buy and
 * Positionsrabatt lines follow the item they belong to. Which digit means which rate is read
 * from the footer tax table, not assumed.
 */
final class AldiReceiptFormat implements ReceiptStoreFormat {

    private static final Pattern MARKER = Pattern.compile("\\bA ?L ?D ?I\\b");
    private static final Pattern ITEM = Pattern.compile("^(.+?)\\s+(" + MONEY + ")\\s*€\\s+([0-9A-Z])$");
    private static final Pattern WEIGHT = Pattern.compile("^(\\d+,\\d{3})\\s*kg\\s*x\\s*(\\d+,\\d{2})\\s*€\\s*/\\s*kg$");
    private static final Pattern MULTI = Pattern.compile("^(\\d+)\\s*x\\s*(\\d+,\\d{2})\\s*€$");
    private static final Pattern POSITIONSRABATT = Pattern.compile("^(Positionsrabatt\\s+\\d+\\s*%)\\s+(" + MONEY + ")\\s*€$");
    private static final Pattern OTHER_RABATT = Pattern.compile("^(.*[Rr]abatt.*?)\\s+(" + MONEY + ")\\s*€$");
    private static final Pattern TOTAL = Pattern.compile("^ZU ZAHLEN\\s+(" + MONEY + ")\\s*€$");
    private static final Pattern SAVINGS = Pattern.compile("^Gesamtersparnis:?\\s*(" + MONEY + ")\\s*€$");
    private static final Pattern DATE = Pattern.compile("Datum\\s+(\\d{2})\\.(\\d{2})\\.(\\d{2,4})\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern POSTAL = Pattern.compile("\\d{5}\\s+\\p{L}");

    @Override
    public StoreFormat format() {
        return StoreFormat.ALDI;
    }

    @Override
    public boolean matches(String rawText) {
        return rawText != null && MARKER.matcher(rawText).find();
    }

    @Override
    public ParsedReceipt parse(List<String> lines) {
        ReceiptBuilder b = new ReceiptBuilder();
        b.brand = "ALDI";
        for (String line : lines) {
            Matcher m;
            if ((m = WEIGHT.matcher(line)).matches() && b.weight(ReceiptText.kilos(m.group(1)), ReceiptText.money(m.group(2)))) {
                continue;
            }
            if ((m = MULTI.matcher(line)).matches()
                    && b.multiBuy(Integer.parseInt(m.group(1)), ReceiptText.money(m.group(2)))) {
                continue;
            }
            if ((m = POSITIONSRABATT.matcher(line)).matches()) {
                b.adjust(AdjustmentType.POSITIONSRABATT, m.group(1), ReceiptText.money(m.group(2)));
                continue;
            }
            if ((m = OTHER_RABATT.matcher(line)).matches()) {
                b.adjust(AdjustmentType.OTHER, m.group(1), ReceiptText.money(m.group(2)));
                continue;
            }
            b.detach();
            if (MARKER.matcher(line).find() && line.length() <= 12) {
                continue;
            }
            if ((m = TOTAL.matcher(line)).matches()) {
                b.total = ReceiptText.money(m.group(1));
            } else if ((m = SAVINGS.matcher(line)).matches()) {
                b.savings = ReceiptText.money(m.group(1));
            } else if ((m = DATE.matcher(line)).find()) {
                b.purchasedAt = ReceiptText.dateTime(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5));
            } else if (ReceiptText.taxRow(line) != null) {
                b.addTax(ReceiptText.taxRow(line));
            } else if (ReceiptText.isTaxHeader(line)) {
                continue;
            } else if ((m = ITEM.matcher(line)).matches()) {
                b.addItem(m.group(1), ReceiptText.money(m.group(2)), m.group(3));
            } else if (!b.hasLines() && b.address == null && POSTAL.matcher(line).find()) {
                b.address = line;
            } else {
                b.unparsed(line);
            }
        }
        return b.build();
    }
}
