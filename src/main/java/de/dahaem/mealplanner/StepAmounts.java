package de.dahaem.mealplanner;

import de.dahaem.shared.Portions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a cooking step's amounts for a number of people. HelloFresh writes step amounts as
 * {@code 1 EL [1,5 EL | 2 EL] Butter*}: the value outside the brackets is for 2 people, the ones
 * inside for 3 and 4 people (a single value inside, {@code 10 g [20 g] Öl*}, is the 4-person one).
 *
 * <ul>
 *   <li>A 2-serving recipe (not a range) uses HelloFresh's own value for 2 / 3 / 4 people and drops
 *       the bracket; a missing size or any other head count scales the 2-person value with
 *       {@link Portions}.</li>
 *   <li>Any other recipe (ready meals, "3 – 4 Personen" ranges) does not trust the 2 / 3 / 4 mapping:
 *       the value outside the bracket is scaled from the recipe's servings (or servingsMax with
 *       {@code useMax}) and the bracket is dropped.</li>
 *   <li>Amounts without a bracket are scaled only when they are a number plus a measuring unit
 *       (g, kg, ml, l, EL, TL, Prise, Msp.); °C, times, Stufe, lengths, Watt, % and plain counts
 *       ("2 Pfannen") are never touched.</li>
 *   <li>Brackets of words ({@code Hälfte [drei Viertel | gesamte]}) or times ({@code [19 Min. | 20 Min.]})
 *       pick HelloFresh's variant for 2 / 3 / 4 people and keep the 2-person text otherwise.</li>
 *   <li>Anything that can't be read safely (free text in brackets, broken numbers, a missing
 *       {@code ]}) stays exactly as written.</li>
 * </ul>
 *
 * A {@code *} after a name marks a pantry item; it is part of the text after the amount and kept.
 * Without {@code people} the step comes back as written, with each readable bracket resolved to its
 * 2-person (outer) value.
 */
public final class StepAmounts {

    private StepAmounts() {
    }

    private static final String FRACTION_CHARS = "½¼¾⅓⅔⅛";

    // One number: "1 1/2", "11/2", "1½", "1 ½", "1,5", "1.5", "½". Order matters (first match wins).
    private static final String NUM = "(?:\\d+ \\d/\\d|\\d+/\\d+|\\d+ ?[" + FRACTION_CHARS + "]|\\d+(?:[.,]\\d+)?|["
            + FRACTION_CHARS + "])";
    // Inside a bracket also "1, 5" (a split decimal).
    private static final String BRACKET_NUM = "(?:\\d+ \\d/\\d|\\d+/\\d+|\\d+ ?[" + FRACTION_CHARS
            + "]|\\d+(?:[.,] ?\\d+)?|[" + FRACTION_CHARS + "])";
    private static final String UNIT_WORD = "(?:°C|%|\\p{L}+\\.?)";
    private static final String RANGE_SEP = "\\s*[–-]\\s*";

    // A number may not continue a word, another number or a range ("1–2 EL", "1.5", "3/4").
    private static final String NUM_START = "(?<![\\p{L}\\d.,/–-])";

    /** A whole bracket alternative: number, optional unit, optionally "– number unit". */
    private static final Pattern ALTERNATIVE = Pattern.compile(
            "(" + BRACKET_NUM + ")\\s*(" + UNIT_WORD + ")?(?:" + RANGE_SEP + "(" + BRACKET_NUM + ")\\s*("
                    + UNIT_WORD + ")?)?");

    /** The amount right before a bracket ("1 EL", "15", "2 – 3 EL"), anchored at the end of the preceding text. */
    private static final Pattern OUTER = Pattern.compile(
            NUM_START + "(" + NUM + ")(?:" + RANGE_SEP + "(" + NUM + "))?(?:\\s*(" + UNIT_WORD + "))?\\s*$");

    /** Separators between the 3- and 4-person values: "|" or a capital I between spaces. */
    private static final Pattern SEPARATOR = Pattern.compile("\\s*\\|\\s*|\\s+I\\s+");

    private static final Pattern WORDS = Pattern.compile("\\p{L}+(?: \\p{L}+)*");
    private static final Pattern TRAILING_WORD = Pattern.compile("(\\p{L}+)\\s*$");

    /** An amount without a bracket: number (or range) + measuring unit. */
    private static final Pattern PLAIN = Pattern.compile(
            NUM_START + "(" + NUM + ")(?:(" + RANGE_SEP + "|\\s+bis\\s+)(" + NUM + "))?( ?)"
                    + "(g|kg|ml|l|EL|TL|Prisen|Prise|Msp\\.|Msp)(?![\\p{L}\\d-])");

    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern CLAUSE_END = Pattern.compile("[.,;:!?]\\s");

    private static final Pattern FOLLOWING_UNIT = Pattern.compile("^\\s*(\\p{L}+\\.?)(?![\\p{L}\\d-])");

    private static final Pattern MULTIPLIER_BEFORE = Pattern.compile("\\d\\s*[x×]\\s*$");

    private static final Set<String> MEASURING_UNITS = Set.of("g", "kg", "ml", "l", "el", "tl", "prise", "prisen",
            "msp.", "msp");
    private static final Set<String> DECIMAL_UNITS = Set.of("g", "kg", "ml", "l");

    // Units that are not amounts: picked per size like words, never scaled.
    private static final Set<String> NON_AMOUNT_UNITS = Set.of("min", "min.", "minute", "minuten", "sek", "sek.",
            "sekunde", "sekunden", "std", "std.", "stunde", "stunden", "°c", "grad", "cm", "mm", "watt", "w", "%",
            "stufe");

    // Words HelloFresh uses for relative amounts ("die Hälfte [drei Viertel | gesamte] Sojasoße").
    private static final Set<String> RELATIVE_WORDS = Set.of("hälfte", "halbe", "halben", "drittel", "viertel",
            "ein", "eine", "einen", "zwei", "drei", "gesamte", "gesamten", "ganze", "ganzen", "komplette",
            "kompletten", "restliche", "restlichen", "rest", "alle", "doppelte", "doppelten");

    /**
     * The step text for {@code people} (null = as written, brackets resolved to the 2-person value).
     *
     * @param servings    the recipe's servings (a range's lower bound)
     * @param servingsMax a range's upper bound, null when the recipe is not a range
     * @param useMax      scale a range recipe from {@code servingsMax} (ignored when it is null)
     * @throws IllegalArgumentException if {@code people} is below 1
     */
    public static String render(String step, int servings, Integer servingsMax, Integer people, boolean useMax) {
        if (people != null && !Portions.isValidPeople(people)) {
            throw new IllegalArgumentException(Portions.INVALID_PEOPLE_MESSAGE);
        }
        if (step == null || step.isEmpty()) {
            return step;
        }
        if (!bracketsBalanced(step)) {
            return step;   // a missing "]" (or "[") — can't tell what belongs to what
        }
        Context ctx = new Context(people, Portions.scalingServings(servings, servingsMax, useMax),
                servings == 2 && servingsMax == null);

        StringBuilder out = new StringBuilder();
        int copiedUpTo = 0;
        Matcher bracket = Pattern.compile("\\[([^\\[\\]]*)]").matcher(step);
        while (bracket.find()) {
            String before = step.substring(copiedUpTo, bracket.start());
            Replacement replacement = resolveBracket(before, bracket.group(1), step.substring(bracket.end()), ctx);
            if (replacement == null) {
                // Leave the bracket and the amount it belongs to exactly as written: a bracket of numbers
                // keeps its whole clause ("1 EL Öl* [1,5 EL | 2 EL]"), anything else the amount right before it.
                Matcher outer = OUTER.matcher(before);
                int keepFrom = outer.find() ? outer.start() : before.length();
                if (DIGIT.matcher(bracket.group(1)).find()) {
                    Matcher clause = CLAUSE_END.matcher(before);
                    keepFrom = 0;
                    while (clause.find()) {
                        keepFrom = clause.end();
                    }
                }
                out.append(scalePlain(before.substring(0, keepFrom), ctx));
                out.append(before.substring(keepFrom)).append(bracket.group());
            } else {
                out.append(scalePlain(before.substring(0, replacement.replaceFrom()), ctx));
                out.append(replacement.text());
            }
            copiedUpTo = bracket.end();
        }
        out.append(scalePlain(step.substring(copiedUpTo), ctx));
        return out.toString();
    }

    private record Context(Integer people, int scaleFrom, boolean trustSizes) {
        boolean scales() {
            return people != null && Portions.isScalable(scaleFrom) && people != scaleFrom;
        }
    }

    /** What replaces {@code before[replaceFrom..] + bracket}. */
    private record Replacement(int replaceFrom, String text) {
    }

    /** A parsed amount: one value or a range, and its unit (null when none). */
    private record Amount(BigDecimal low, BigDecimal high, String unit) {
        Amount withUnit(String newUnit) {
            return new Amount(low, high, newUnit);
        }
    }

    private static boolean bracketsBalanced(String step) {
        int depth = 0;
        for (char c : step.toCharArray()) {
            if (c == '[') {
                if (++depth > 1) {
                    return false;
                }
            } else if (c == ']') {
                if (--depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    /** The replacement for an amount + bracket ending {@code before}, or null when it can't be read safely. */
    private static Replacement resolveBracket(String before, String content, String after, Context ctx) {
        List<String> alternatives = splitAlternatives(content);
        if (alternatives == null) {
            return null;
        }
        Matcher outer = OUTER.matcher(before);
        if (outer.find()) {
            return resolveAmountBracket(before, outer, alternatives, followingUnit(after), ctx);
        }
        return resolveWordBracket(before, alternatives, ctx);
    }

    private static List<String> splitAlternatives(String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : SEPARATOR.split(trimmed, -1)) {
            String p = part.trim();
            if (p.isEmpty()) {
                return null;
            }
            parts.add(p);
        }
        return parts.size() <= 2 ? parts : null;
    }

    private static Replacement resolveAmountBracket(String before, Matcher outer, List<String> alternatives,
                                                    String followingUnit, Context ctx) {
        String outerText = before.substring(outer.start()).stripTrailing();
        String outerUnit = outer.group(3);
        Amount outerAmount = amount(outer.group(1), outer.group(2), outerUnit);
        if (outerAmount == null) {
            return null;
        }
        List<Amount> sizes = new ArrayList<>();
        for (String alternative : alternatives) {
            Amount parsed = parseAlternative(alternative);
            if (parsed == null) {
                return null;
            }
            if (parsed.unit() == null) {
                parsed = parsed.withUnit(outerUnit);   // "150 g [225 | 300]" means grams inside too
            } else if (outerUnit == null) {
                return null;   // "15 [30 g] g" — can't tell which unit wins
            }
            if (isNonAmount(parsed.unit()) != isNonAmount(outerUnit)) {
                return null;   // "10 Min. [2 EL]" mixes kinds
            }
            sizes.add(parsed);
        }
        int from = outer.start();
        Amount chosen = exactSize(sizes, ctx);
        if (chosen != null) {
            return new Replacement(from, format(chosen, followingUnit));
        }
        if (isNonAmount(outerUnit) || !ctx.scales()) {
            return new Replacement(from, outerText);   // times etc. keep the 2-person value
        }
        return new Replacement(from, format(scale(outerAmount, ctx), followingUnit));
    }

    /** The measuring unit right after a bracket ("15 [30] g Butter" → g), only to format the number; else null. */
    private static String followingUnit(String after) {
        Matcher m = FOLLOWING_UNIT.matcher(after);
        return m.find() && MEASURING_UNITS.contains(m.group(1).toLowerCase(Locale.ROOT)) ? m.group(1) : null;
    }

    /** HelloFresh's own value for the requested size, or null (2 people, missing size, other counts, untrusted). */
    private static <T> T exactSize(List<T> sizes, Context ctx) {
        if (!ctx.trustSizes() || ctx.people() == null) {
            return null;
        }
        if (ctx.people() == 3 && sizes.size() == 2) {
            return sizes.get(0);
        }
        if (ctx.people() == 4) {
            return sizes.get(sizes.size() - 1);
        }
        return null;
    }

    private static Replacement resolveWordBracket(String before, List<String> alternatives, Context ctx) {
        for (String alternative : alternatives) {
            if (!WORDS.matcher(alternative).matches() || !allRelative(alternative)) {
                return null;   // free text ("[und im Varoma-Behälter]")
            }
        }
        Matcher word = TRAILING_WORD.matcher(before);
        if (!word.find() || !RELATIVE_WORDS.contains(word.group(1).toLowerCase(Locale.ROOT))) {
            return null;
        }
        // "drei Viertel [gesamte]": the outer is more than one word — not safe to swap.
        Matcher previous = TRAILING_WORD.matcher(before.substring(0, word.start()));
        if (previous.find() && RELATIVE_WORDS.contains(previous.group(1).toLowerCase(Locale.ROOT))) {
            return null;
        }
        String chosen = exactSize(alternatives, ctx);
        return new Replacement(word.start(), chosen != null ? chosen : word.group(1));
    }

    private static boolean allRelative(String words) {
        for (String w : words.split(" ")) {
            if (!RELATIVE_WORDS.contains(w.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static Amount parseAlternative(String text) {
        Matcher m = ALTERNATIVE.matcher(text);
        if (!m.matches()) {
            return null;
        }
        String unit = m.group(2);
        String highUnit = m.group(4);
        if (m.group(3) == null && highUnit != null) {
            return null;
        }
        if (unit != null && highUnit != null && !unit.equals(highUnit)) {
            return null;
        }
        if (unit != null && m.group(3) != null && highUnit == null) {
            return null;   // "4 EL – 8" — ambiguous
        }
        return amount(m.group(1), m.group(3), highUnit != null ? highUnit : unit);
    }

    private static Amount amount(String low, String high, String unit) {
        BigDecimal lowValue = parseNumber(low);
        BigDecimal highValue = high == null ? null : parseNumber(high);
        if (lowValue == null || (high != null && highValue == null)) {
            return null;
        }
        return new Amount(lowValue, highValue, unit);
    }

    /** Parses one number as HelloFresh writes it; null when it isn't a plain amount. */
    static BigDecimal parseNumber(String raw) {
        String s = raw.replace(" ", "");
        char last = s.charAt(s.length() - 1);
        if (FRACTION_CHARS.indexOf(last) >= 0) {
            BigDecimal whole = s.length() == 1 ? BigDecimal.ZERO : new BigDecimal(s.substring(0, s.length() - 1));
            return whole.add(fractionValue(last));
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            String numerator = s.substring(0, slash);
            int denominator = Integer.parseInt(s.substring(slash + 1));
            if (denominator == 0) {
                return null;
            }
            BigDecimal whole = BigDecimal.ZERO;
            if (raw.contains(" ")) {   // "1 1/2"
                whole = new BigDecimal(raw.substring(0, raw.indexOf(' ')));
                numerator = raw.substring(raw.indexOf(' ') + 1, raw.indexOf('/'));
            } else if (numerator.length() >= 2) {
                // "11/2" is HelloFresh's "1 1/2": the last digit before the slash is the numerator when it
                // is smaller than the denominator.
                int lastDigit = numerator.charAt(numerator.length() - 1) - '0';
                if (lastDigit == 0 || lastDigit >= denominator) {
                    return null;
                }
                whole = new BigDecimal(numerator.substring(0, numerator.length() - 1));
                numerator = String.valueOf(lastDigit);
            }
            return whole.add(new BigDecimal(numerator).divide(BigDecimal.valueOf(denominator), 3,
                    RoundingMode.HALF_UP));
        }
        return new BigDecimal(s.replace(',', '.'));
    }

    private static BigDecimal fractionValue(char c) {
        return switch (c) {
            case '½' -> new BigDecimal("0.5");
            case '¼' -> new BigDecimal("0.25");
            case '¾' -> new BigDecimal("0.75");
            case '⅓' -> new BigDecimal("0.333");
            case '⅔' -> new BigDecimal("0.667");
            default -> new BigDecimal("0.125");   // ⅛
        };
    }

    private static boolean isNonAmount(String unit) {
        return unit != null && NON_AMOUNT_UNITS.contains(unit.toLowerCase(Locale.ROOT));
    }

    private static Amount scale(Amount amount, Context ctx) {
        return new Amount(Portions.scaleRounded(amount.low(), ctx.people(), ctx.scaleFrom()),
                amount.high() == null ? null : Portions.scaleRounded(amount.high(), ctx.people(), ctx.scaleFrom()),
                amount.unit());
    }

    /** Scales the measuring-unit amounts in a stretch of text without brackets. */
    private static String scalePlain(String text, Context ctx) {
        if (!ctx.scales() || text.isEmpty()) {
            return text;
        }
        Matcher m = PLAIN.matcher(text);
        StringBuilder out = new StringBuilder();
        int copied = 0;
        while (m.find()) {
            if (MULTIPLIER_BEFORE.matcher(text.substring(0, m.start())).find()) {
                continue;   // "2 x 100 g" is a pack size, not an amount
            }
            BigDecimal low = parseNumber(m.group(1));
            BigDecimal high = m.group(3) == null ? null : parseNumber(m.group(3));
            if (low == null || (m.group(3) != null && high == null)) {
                continue;
            }
            String unit = m.group(5);
            BigDecimal scaledLow = Portions.scaleRounded(low, ctx.people(), ctx.scaleFrom());
            String replacement = formatNumber(scaledLow, unit);
            if (high != null) {
                replacement += m.group(2) + formatNumber(Portions.scaleRounded(high, ctx.people(), ctx.scaleFrom()),
                        unit);
            }
            out.append(text, copied, m.start()).append(replacement).append(m.group(4)).append(unit);
            copied = m.end();
        }
        out.append(text.substring(copied));
        return out.toString();
    }

    /** The amount as text; {@code followingUnit} only steers the number format of an amount without a unit. */
    private static String format(Amount amount, String followingUnit) {
        String formatUnit = amount.unit() != null ? amount.unit() : followingUnit;
        String text = formatNumber(amount.low(), formatUnit);
        if (amount.high() != null) {
            text += " – " + formatNumber(amount.high(), formatUnit);
        }
        return amount.unit() == null ? text : text + " " + amount.unit();
    }

    /**
     * German number formatting: decimals with a comma for g / kg / ml / l and non-amount units (grams and
     * millilitres to one decimal, from 100 up as whole numbers), ½ ¼ ¾ ⅓ ⅔ for spoons, pinches and counts
     * where they fit, otherwise a decimal.
     */
    static String formatNumber(BigDecimal value, String unit) {
        String u = unit == null ? "" : unit.toLowerCase(Locale.ROOT);
        BigDecimal v = value.setScale(2, RoundingMode.HALF_UP);
        if (DECIMAL_UNITS.contains(u) || isNonAmount(unit)) {
            if (u.equals("g") || u.equals("ml")) {
                v = v.setScale(v.compareTo(BigDecimal.valueOf(100)) >= 0 ? 0 : 1, RoundingMode.HALF_UP);
            }
            return decimal(v);
        }
        BigDecimal whole = v.setScale(0, RoundingMode.DOWN);
        String fraction = switch (v.subtract(whole).movePointRight(2).intValue()) {
            case 0 -> "";
            case 25 -> "¼";
            case 50 -> "½";
            case 75 -> "¾";
            case 33 -> "⅓";
            case 67 -> "⅔";
            default -> null;
        };
        if (fraction == null) {
            return decimal(v);
        }
        if (fraction.isEmpty()) {
            return whole.toPlainString();
        }
        return whole.signum() == 0 ? fraction : whole.toPlainString() + fraction;
    }

    private static String decimal(BigDecimal v) {
        BigDecimal stripped = v.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString().replace('.', ',');
    }
}
