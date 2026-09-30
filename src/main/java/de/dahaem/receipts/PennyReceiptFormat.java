package de.dahaem.receipts;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.dahaem.receipts.ReceiptText.MONEY;

/**
 * PENNY (REWE Group) layout. Item lines end in a tax group letter ("NAME  2,79 B") -- the
 * opposite letter meaning to ALDI's digits, which is why the mapping is read from the footer.
 * Discounts, Pfand, Leergut and Treueartikel are recognised before the generic item pattern,
 * because several of them end in "amount letter" too.
 */
final class PennyReceiptFormat implements ReceiptStoreFormat {

    private static final Pattern MARKER = Pattern.compile("(?i)\\bPENNY\\b");
    private static final Pattern MARKT = Pattern.compile("^Markt:\\s*(\\d+)");
    private static final Pattern UID = Pattern.compile("(?i)^UID\\s*Nr");
    private static final Pattern ITEM = Pattern.compile("^(.+?)\\s+(" + MONEY + ")\\s+([A-Z])$");
    private static final Pattern WEIGHT = Pattern.compile("^(\\d+,\\d{3})\\s*kg\\s*x\\s*(\\d+,\\d{2})\\s*EUR\\s*/\\s*kg$");
    private static final Pattern MULTI = Pattern.compile("^(\\d+)\\s*Stk\\s*x\\s*(\\d+,\\d{2})$");
    private static final Pattern APP_RABATT = Pattern.compile("^(App-Preis-Rabatt)\\s+(" + MONEY + ")\\s+[A-Z]$");
    private static final Pattern PFAND = Pattern.compile("^(PFAND\\s+\\d+,\\d{2}\\s*EURO)\\s+(" + MONEY + ")\\s+[A-Z]\\s*\\*?$");
    private static final Pattern LEERGUT_HEADER = Pattern.compile("^LEERG\\b.*");
    private static final Pattern LEERGUT_RETURN = Pattern.compile("^(\\d+)\\s*Stk\\s*x\\s*(\\d+,\\d{2})\\s+(" + MONEY + ")\\s+[A-Z]$");
    private static final Pattern TREUEARTIKEL = Pattern.compile("^(Treueartikel)\\s+(" + MONEY + ")$");
    private static final Pattern TREUEPUNKTE = Pattern.compile("(?i)^Treuepunkte.*(diesen Einkauf|erhalten|gesammelt)\\D*(\\d+)\\s*$");
    private static final Pattern TOTAL = Pattern.compile("^SUMME\\s+EUR\\s+(" + MONEY + ")$");
    private static final Pattern DATE = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{4})\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern POSTAL = Pattern.compile("^\\d{5}\\s+\\p{L}");

    @Override
    public StoreFormat format() {
        return StoreFormat.PENNY;
    }

    @Override
    public boolean matches(String rawText) {
        if (rawText == null) {
            return false;
        }
        return MARKER.matcher(rawText).find()
                || (Pattern.compile("(?im)^\\s*UID\\s*Nr").matcher(rawText).find()
                && Pattern.compile("(?m)^\\s*Markt:\\s*\\d+").matcher(rawText).find());
    }

    @Override
    public ParsedReceipt parse(List<String> lines) {
        ReceiptBuilder b = new ReceiptBuilder();
        b.brand = "PENNY";
        String street = null;
        boolean leergutPending = false;
        for (String line : lines) {
            Matcher m;
            if ((m = APP_RABATT.matcher(line)).matches()) {
                b.adjust(AdjustmentType.APP_RABATT, m.group(1), ReceiptText.money(m.group(2)));
            } else if ((m = PFAND.matcher(line)).matches()) {
                b.adjust(AdjustmentType.PFAND_DEPOSIT, m.group(1), ReceiptText.money(m.group(2)));
            } else if ((m = TREUEARTIKEL.matcher(line)).matches()) {
                b.adjust(AdjustmentType.LOYALTY_STICKER, m.group(1), ReceiptText.money(m.group(2)));
            } else if (leergutPending && (m = LEERGUT_RETURN.matcher(line)).matches()) {
                b.detach();
                b.adjust(AdjustmentType.LEERGUT_RETURN, "Leergut " + m.group(1) + " Stk x " + m.group(2),
                        ReceiptText.money(m.group(3)));
                leergutPending = false;
            } else if ((m = WEIGHT.matcher(line)).matches()
                    && b.weight(ReceiptText.kilos(m.group(1)), ReceiptText.money(m.group(2)))) {
                // handled
            } else if ((m = MULTI.matcher(line)).matches()
                    && b.multiBuy(Integer.parseInt(m.group(1)), ReceiptText.money(m.group(2)))) {
                // handled
            } else {
                b.detach();
                if (leergutPending) {
                    b.unparsed("LEERG. (no return line followed)");
                    leergutPending = false;
                }
                if (LEERGUT_HEADER.matcher(line).matches()) {
                    leergutPending = true;
                } else if ((m = TOTAL.matcher(line)).matches()) {
                    b.total = ReceiptText.money(m.group(1));
                } else if ((m = TREUEPUNKTE.matcher(line)).matches()) {
                    b.addLoyaltyPoints(Integer.parseInt(m.group(2)));
                } else if ((m = MARKT.matcher(line)).find()) {
                    b.storeId = m.group(1);
                } else if (ReceiptText.taxRow(line) != null) {
                    b.addTax(ReceiptText.taxRow(line));
                } else if (ReceiptText.isTaxHeader(line)) {
                    // column header of the tax table
                } else if (!b.hasLines() && MARKER.matcher(line).find() && line.length() <= 12) {
                    // brand line
                } else if ((m = DATE.matcher(line)).find()) {
                    b.purchasedAt = ReceiptText.dateTime(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5));
                } else if ((m = ITEM.matcher(line)).matches()) {
                    b.addItem(m.group(1), ReceiptText.money(m.group(2)), m.group(3));
                } else if (!b.hasLines() && b.address == null && POSTAL.matcher(line).find()) {
                    b.address = street == null ? line : street + ", " + line;
                } else if (!b.hasLines() && b.address == null && !UID.matcher(line).find() && line.matches(".*\\p{L}.*\\d+.*")) {
                    street = line;
                } else {
                    b.unparsed(line);
                }
            }
        }
        if (leergutPending) {
            b.unparsed("LEERG. (no return line followed)");
        }
        return b.build();
    }
}
