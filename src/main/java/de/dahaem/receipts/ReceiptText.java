package de.dahaem.receipts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared text helpers for the store handlers: number parsing, privacy filter, tax-table rows. */
final class ReceiptText {

    static final String MONEY = "[-−]?\\d+,\\d{2}";

    // Payment / technical footer data we must not retain. Dropped before any handler sees a line.
    private static final Pattern SENSITIVE_WORDS = Pattern.compile(
            "(?i)\\b(karten-?nr|kartennummer|kartenfolge|terminal-?(id|nr)|ta-?nr|trace-?nr|vu-?nr|pos-?info|as-?zeit"
                    + "|transaktions-?(nr|nummer)|tse|signatur\\w*|serienn+ummer|iban|emv|arqc)\\b");
    private static final Pattern SENSITIVE_CODES = Pattern.compile("\\b(PAN|AID|TVR|TSI)\\b");
    private static final Pattern MASKED_CARD = Pattern.compile("[*#Xx•]{4,}\\s?\\d{2,4}");
    private static final Pattern LONG_DIGITS = Pattern.compile("(?:\\d[ -]?){13,19}");
    private static final Pattern LONG_TOKEN = Pattern.compile("\\S{40,}");
    // Wrapped signature/certificate text: one base64-looking token on its own line (the tail of a
    // wrapped signature can be short, so length alone isn't enough).
    private static final Pattern BASE64_LINE = Pattern.compile("^[A-Za-z0-9+/]{8,}={0,2}$");
    private static final Pattern BASE64_HINT = Pattern.compile("[+/=]|(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])");
    private static final Pattern SEPARATOR = Pattern.compile("^[-=_*.~\\s]{3,}$");

    private static final Pattern TAX_ROW = Pattern.compile(
            "^([A-Z0-9])\\s*[=:]?\\s*(\\d{1,2},\\d{1,2})\\s*%(.*)$");
    private static final Pattern TAX_HEADER = Pattern.compile("(?i)^(mwst|steuer|ust)\\b.*(netto|brutto).*$");
    private static final Pattern MONEY_FIND = Pattern.compile(MONEY);

    private ReceiptText() {
    }

    /** Non-blank, trimmed lines with separators and payment/technical data removed. */
    static List<String> lines(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String line : raw.split("\\R")) {
            String s = line.replace(' ', ' ').strip();
            if (s.isEmpty() || SEPARATOR.matcher(s).matches() || isSensitive(s)) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    static boolean isSensitive(String s) {
        return SENSITIVE_WORDS.matcher(s).find() || SENSITIVE_CODES.matcher(s).find()
                || MASKED_CARD.matcher(s).find() || LONG_DIGITS.matcher(s).find()
                || LONG_TOKEN.matcher(s).find() || isBase64Line(s);
    }

    private static boolean isBase64Line(String s) {
        return BASE64_LINE.matcher(s).matches() && BASE64_HINT.matcher(s).find();
    }

    /** "1.234,56" / "-2,20" / "0,865" to BigDecimal (German decimal comma). */
    static BigDecimal number(String s) {
        String t = s.strip().replace('−', '-');
        if (t.contains(",")) {
            t = t.replace(".", "").replace(',', '.');
        }
        return new BigDecimal(t);
    }

    static BigDecimal money(String s) {
        return number(s).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal kilos(String s) {
        return number(s).setScale(3, RoundingMode.HALF_UP);
    }

    static LocalDateTime dateTime(String day, String month, String year, String hour, String minute) {
        int y = Integer.parseInt(year);
        if (year.length() == 2) {
            y += 2000;
        }
        try {
            return LocalDateTime.of(y, Integer.parseInt(month), Integer.parseInt(day),
                    Integer.parseInt(hour), Integer.parseInt(minute));
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    static boolean isTaxHeader(String line) {
        return TAX_HEADER.matcher(line).matches();
    }

    /** A footer tax-table row such as "1  7,00%  24,30  1.70  26,00" or "A= 19,0%  ..."; null if not one. */
    static TaxBreakdownEntry taxRow(String line) {
        Matcher m = TAX_ROW.matcher(line);
        if (!m.matches()) {
            return null;
        }
        BigDecimal rate = number(m.group(2)).setScale(2, RoundingMode.HALF_UP);
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher a = MONEY_FIND.matcher(m.group(3));
        while (a.find()) {
            amounts.add(money(a.group()));
        }
        if (amounts.size() == 3) {
            return new TaxBreakdownEntry(m.group(1), rate, amounts.get(0), amounts.get(1), amounts.get(2));
        }
        return new TaxBreakdownEntry(m.group(1), rate, null, null, null);
    }
}
