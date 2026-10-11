package de.dahaem.receipts;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.dahaem.receipts.ReceiptText.MONEY;

/**
 * PENNY (REWE Group) layout. Item lines end in a tax group letter ("NAME  2,79 B") -- the
 * opposite letter meaning to ALDI's digits, which is why the mapping is read from the footer.
 * A trailing " *" after the letter marks a product excluded from discounts. Discounts, Pfand,
 * Leergut and Treueartikel are recognised before the generic item pattern, because several of
 * them end in "amount letter" too. Known header/footer and card-payment lines are recognised and
 * skipped; card number, Terminal-ID, Trace-Nr., Pos-Info, AS-Zeit and signature/serial lines are
 * never kept (most are already dropped by {@link ReceiptText#lines}).
 */
final class PennyReceiptFormat implements ReceiptStoreFormat {

    private static final Pattern MARKER = Pattern.compile("(?i)\\bPENNY\\b|P\\s?E\\s?N\\s?N\\s?Y\\s?-\\s?M\\s?A\\s?R\\s?K\\s?T");
    private static final Pattern MARKT = Pattern.compile("^Markt:\\s*(\\d+)");
    private static final Pattern UID = Pattern.compile("(?i)^UID\\s*Nr");
    private static final Pattern ITEM = Pattern.compile("^(.+?)\\s+(" + MONEY + ")\\s+([A-Z])(\\s*\\*)?$");
    private static final Pattern WEIGHT = Pattern.compile("^(\\d+,\\d{3})\\s*kg\\s*x\\s*(\\d+,\\d{2})\\s*EUR\\s*/\\s*kg$");
    private static final Pattern MULTI = Pattern.compile("^(\\d+)\\s*Stk\\s*x\\s*(\\d+,\\d{2})$");
    private static final Pattern APP_RABATT = Pattern.compile("^(App-Preis-Rabatt)\\s+(" + MONEY + ")\\s+[A-Z](\\s*\\*)?$");
    private static final Pattern OTHER_RABATT = Pattern.compile("^(.*[Rr]abatt.*?)\\s+([-−]\\d+,\\d{2})(\\s+[A-Z])?(\\s*\\*)?$");
    private static final Pattern PFAND = Pattern.compile("^(PFAND\\s+\\d+,\\d{2}\\s*EURO?)\\s+(" + MONEY + ")\\s+[A-Z](\\s*\\*)?$");
    /** A deposit return on one line: "LEERGUT EINWEG  -0,75 A *", "LEERG. EW E. ST  -0,50 B *". */
    private static final Pattern DEPOSIT_RETURN = Pattern.compile(
            "^((?:LEERG|PFAND\\s*-?\\s*R(?:Ü|UE)CK)\\S*(?:\\s.*?)?)\\s+([-−]\\d+,\\d{2})\\s+[A-Z](\\s*\\*)?$");
    /** Older layout: "LEERG. ..." on its own, amount on the following "n Stk x p  -x,xx B" line. */
    private static final Pattern LEERGUT_HEADER = Pattern.compile("^LEERG\\b.*");
    private static final Pattern LEERGUT_RETURN = Pattern.compile("^(\\d+)\\s*Stk\\s*x\\s*(\\d+,\\d{2})\\s+(" + MONEY + ")\\s+[A-Z](\\s*\\*)?$");
    private static final Pattern TREUEARTIKEL = Pattern.compile("^(Treueartikel)\\s+(" + MONEY + ")(\\s+[A-Z])?(\\s*\\*)?$");
    private static final Pattern TREUEPUNKTE = Pattern.compile("(?i)^Treuepunkte.*(diesen Einkauf|erhalten|gesammelt)\\D*(\\d+)\\s*$");
    private static final Pattern SIE_ERHALTEN = Pattern.compile("(?i)^(?:Sie erhalten|Du erh(?:ä|ae)ls?t)\\s+(\\d+)\\s+Treuepunkt");
    private static final Pattern APP_SAVINGS = Pattern.compile("(?i)^Mit der PENNY[- ]App\\b.*?(\\d+,\\d{2})\\s*EUR\\b.*gespart");
    /** Two-line form: "Mit der PENNY App" then "1,00 EUR gespart". */
    private static final Pattern APP_SAVINGS_HEAD = Pattern.compile("(?i)^Mit der PENNY[- ]App:?$");
    private static final Pattern APP_SAVINGS_TAIL = Pattern.compile("(?i)^(\\d+,\\d{2})\\s*EUR\\s+gespart\\W*$");
    private static final Pattern OTHER_SAVINGS = Pattern.compile("(?i)^Sonstige Vorteile\\D*?(\\d+,\\d{2})");
    /** "Deine zusätzlichen Vorteile heute:" block, closed by "Summe x,xx" (the block's sum). */
    private static final Pattern BENEFITS_HEAD = Pattern.compile("(?i)^Deine zus(?:ä|ae)tzlichen Vorteile\\b.*");
    private static final Pattern BENEFITS_SUM = Pattern.compile("^Summe\\s+(\\d+,\\d{2})$");
    private static final Pattern RECEIPT_NUMBER = Pattern.compile("(?i)^Beleg-?Nr\\.?:?\\s*(\\d+)$");
    private static final Pattern TOTAL = Pattern.compile("^SUMME\\s+EUR\\s+(" + MONEY + ")$");
    private static final Pattern DATE = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{4})\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern DATE_ONLY = Pattern.compile("^Datum:?\\s*(\\d{2})\\.(\\d{2})\\.(\\d{4})$");
    private static final Pattern TIME_ONLY = Pattern.compile("^Uhrzeit:?\\s*(\\d{1,2}):(\\d{2})(?::\\d{2})?(?:\\s*Uhr)?$");
    private static final Pattern POSTAL = Pattern.compile("^\\d{5}\\s+\\p{L}");

    /**
     * Lines with no data we keep: banner, column header, payment and card block, tax-table total,
     * closing texts. The card-detail lines among them (card number, Terminal-ID, Trace-Nr.,
     * Pos-Info, AS-Zeit) are matched here too, so they are skipped even if the generic filter
     * misses a variant.
     */
    private static final Pattern SKIP = Pattern.compile("(?i)^(?:"
            + "\\*+\\s*P\\s?E\\s?N\\s?N\\s?Y.*"                       // *** P E N N Y - M A R K T GmbH ***
            + "|UID\\s*Nr.*"
            + "|EUR"                                                  // column header above the items
            + "|Geg\\.\\s.*|R(?:ü|ue)ckgeld\\b.*"
            + "|\\*\\s*\\*\\s*(?:Kunden|H(?:ä|ae)ndler)beleg\\s*\\*\\s*\\*"
            + "|Datum\\b.*|Uhrzeit\\b.*"
            + "|Trace-?Nr.*|Terminal-?ID.*|Pos-?Info.*|AS-?Zeit.*|AS-?Proc-?Code.*|Capt\\.?-?Ref.*|(?-i:Nr\\.)(?:\\s.*)?"
            + "|Kartenzahlung\\b.*|Bezahlung|Contactless|girocard\\b.*|Betrag\\s+EUR\\b.*|Zahlung erfolgt.*"
            + "|APPROVED|GENEHMIGT"
            + "|Gesamtbetrag\\b.*"
            + "|Serienn+ummer\\b.*"
            + ")$");

    /** Closing texts below the tax table. */
    private static final Pattern CLOSING = Pattern.compile("(?i)^(?:"
            + "Wir danken\\b.*|Danke f(?:ü|ue)r (?:deinen|Ihren) Einkauf.*"
            + "|Erstmal (?:zu|in die) Penny.*|Noch mehr sparen:?"
            + "|Keine Rabatte auf die mit \\*.*|gekennzeichnete Produkte\\.?"
            + "|-?\\s*auch im Internet unter.*|.*\\bwww\\.penny\\.de\\b.*"
            + ")$");

    /**
     * First line of the payment block (amount given, card slip). Everything from here up to the
     * tax table or the first known footer line is payment data and is skipped whatever it says --
     * girocard, VISA, Mastercard, bank names, approval codes, a lone amount line -- so none of it
     * can reach {@code unparsedLines}.
     */
    private static final Pattern PAYMENT_START = Pattern.compile("(?i)^(?:"
            + "Geg\\.\\s.*|\\*\\s*\\*\\s*(?:Kunden|H(?:ä|ae)ndler)beleg\\s*\\*\\s*\\*"
            + "|Kartenzahlung\\b.*|Bezahlung|Betrag\\s+EUR\\b.*"
            + ")$");

    /** What the last booked line was, so an "n Stk x p" sub-line lands on the right thing. */
    private enum Last { OTHER, PFAND, RETURN }

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
        Last last = Last.OTHER;
        String[] date = null;
        String[] time = null;
        boolean inPayment = false;
        boolean appSavingsPending = false;
        boolean sawBenefitsSum = false;
        for (String line : lines) {
            Matcher m;
            if (inPayment || PAYMENT_START.matcher(line).matches()) {
                if (!inPayment) {
                    b.detach();
                    if (leergutPending) {
                        b.unparsed("LEERG. (no return line followed)");
                        leergutPending = false;
                    }
                    inPayment = true;
                    last = Last.OTHER;
                    continue;
                }
                if (ReceiptText.isTaxHeader(line) || ReceiptText.taxRow(line) != null || isFooter(line)) {
                    inPayment = false; // end of the payment block; parse this line normally
                } else {
                    if ((m = RECEIPT_NUMBER.matcher(line)).matches()) {
                        b.receiptNumber = m.group(1);
                    } else if ((m = DATE.matcher(line)).find()) {
                        b.purchasedAt = ReceiptText.dateTime(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5));
                    } else if ((m = DATE_ONLY.matcher(line)).matches()) {
                        date = new String[] {m.group(1), m.group(2), m.group(3)};
                    } else if ((m = TIME_ONLY.matcher(line)).matches()) {
                        time = new String[] {m.group(1), m.group(2)};
                    }
                    continue; // payment data: never kept
                }
            }
            if (appSavingsPending) {
                appSavingsPending = false;
                if ((m = APP_SAVINGS_TAIL.matcher(line)).matches()) {
                    b.appSavings = ReceiptText.money(m.group(1));
                    continue;
                }
            }
            Last now = Last.OTHER;
            if ((m = APP_RABATT.matcher(line)).matches()) {
                b.adjust(AdjustmentType.APP_RABATT, m.group(1), ReceiptText.money(m.group(2)));
            } else if ((m = PFAND.matcher(line)).matches()) {
                b.adjust(AdjustmentType.PFAND_DEPOSIT, m.group(1), ReceiptText.money(m.group(2)));
                now = Last.PFAND;
            } else if ((m = TREUEARTIKEL.matcher(line)).matches()) {
                b.adjust(AdjustmentType.LOYALTY_STICKER, m.group(1), ReceiptText.money(m.group(2)));
            } else if ((m = DEPOSIT_RETURN.matcher(line)).matches()) {
                b.detach();
                if (leergutPending) {
                    b.unparsed("LEERG. (no return line followed)");
                    leergutPending = false;
                }
                b.adjust(AdjustmentType.LEERGUT_RETURN, m.group(1).strip(), ReceiptText.money(m.group(2)));
                now = Last.RETURN;
            } else if (leergutPending && (m = LEERGUT_RETURN.matcher(line)).matches()) {
                b.detach();
                b.adjust(AdjustmentType.LEERGUT_RETURN, "Leergut " + m.group(1) + " Stk x " + m.group(2),
                        ReceiptText.money(m.group(3)));
                leergutPending = false;
            } else if (last != Last.OTHER && (m = MULTI.matcher(line)).matches()) {
                // count line of a multi-unit Pfand or Leergut line, not of the product
                b.annotateLastAdjustment(Integer.parseInt(m.group(1)), ReceiptText.money(m.group(2)), line);
            } else if ((m = OTHER_RABATT.matcher(line)).matches()) {
                b.adjust(AdjustmentType.OTHER, m.group(1).strip(), ReceiptText.money(m.group(2)));
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
                } else if ((m = SIE_ERHALTEN.matcher(line)).find()) {
                    b.addLoyaltyPoints(Integer.parseInt(m.group(1)));
                } else if ((m = APP_SAVINGS.matcher(line)).find()) {
                    b.appSavings = ReceiptText.money(m.group(1));
                } else if (APP_SAVINGS_HEAD.matcher(line).matches()) {
                    appSavingsPending = true;
                } else if ((m = OTHER_SAVINGS.matcher(line)).find()) {
                    if (!sawBenefitsSum) {
                        b.otherSavings = ReceiptText.money(m.group(1));
                    }
                } else if (BENEFITS_HEAD.matcher(line).matches()) {
                    // heading of the "Sonstige Vorteile ... Summe" block
                } else if (b.total != null && (m = BENEFITS_SUM.matcher(line)).matches()) {
                    b.otherSavings = ReceiptText.money(m.group(1)); // the block's own sum wins
                    sawBenefitsSum = true;
                } else if ((m = RECEIPT_NUMBER.matcher(line)).matches()) {
                    b.receiptNumber = m.group(1);
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
                } else if ((m = DATE_ONLY.matcher(line)).matches()) {
                    date = new String[] {m.group(1), m.group(2), m.group(3)};
                } else if ((m = TIME_ONLY.matcher(line)).matches()) {
                    time = new String[] {m.group(1), m.group(2)};
                } else if (SKIP.matcher(line).matches() || CLOSING.matcher(line).matches()) {
                    // known header/footer/payment line without data we keep
                } else if ((m = ITEM.matcher(line)).matches()) {
                    b.addItem(m.group(1), ReceiptText.money(m.group(2)), m.group(3), m.group(4) != null);
                } else if (!b.hasLines() && b.address == null && POSTAL.matcher(line).find()) {
                    b.address = street == null ? line : street + ", " + line;
                } else if (!b.hasLines() && b.address == null && !UID.matcher(line).find() && line.matches(".*\\p{L}.*\\d+.*")) {
                    street = line;
                } else {
                    b.unparsed(line);
                }
            }
            last = now;
        }
        if (leergutPending) {
            b.unparsed("LEERG. (no return line followed)");
        }
        // The card block prints date and time on separate lines; use them if no header date was found.
        if (b.purchasedAt == null && date != null && time != null) {
            b.purchasedAt = ReceiptText.dateTime(date[0], date[1], date[2], time[0], time[1]);
        }
        if (b.appSavings != null || b.otherSavings != null) {
            b.savings = nz(b.appSavings).add(nz(b.otherSavings));
        }
        return b.build();
    }

    /** A known line below the payment block; ends it. */
    private static boolean isFooter(String line) {
        return TREUEPUNKTE.matcher(line).matches() || SIE_ERHALTEN.matcher(line).find()
                || APP_SAVINGS.matcher(line).find() || APP_SAVINGS_HEAD.matcher(line).matches()
                || OTHER_SAVINGS.matcher(line).find() || BENEFITS_HEAD.matcher(line).matches()
                || CLOSING.matcher(line).matches();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
