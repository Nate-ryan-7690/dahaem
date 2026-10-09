package de.dahaem.stores;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.dahaem.stores.JsonUtil.firstNumber;
import static de.dahaem.stores.JsonUtil.text;

/**
 * Reads Penny's weekly offers from the JSON API behind www.penny.de/angebote.
 *
 *  1. /.rest/market lists every Penny store with its price region ("sellingRegion").
 *  2. The offers page names the current / next calendar week (data-current-week,
 *     data-next-week) and the offer categories per week with the day they start
 *     (Mo-Sa, Do-Sa, Fr-Sa; see categoryStarts). Next week's offers appear there from Friday.
 *  3. /.rest/offers/by-category/{week}/{category}?region={sellingRegion}
 *     returns the offer tiles of one category.
 *
 * Penny only publishes offers, not its full range, and prices depend on the
 * store's region, so the store id comes from config.
 */
public class PennyClient {

    private static final String BASE = "https://www.penny.de";
    private static final Pattern CATEGORY = Pattern.compile("data-category-name=\"([^\"]+)\"");
    private static final Pattern CURRENT_WEEK = Pattern.compile("data-current-week=\"(\\d{4}-\\d{1,2})\"");
    private static final Pattern NEXT_WEEK = Pattern.compile("data-next-week=\"(\\d{4}-\\d{1,2})\"");
    private static final Pattern SECTION = Pattern.compile(
            "id=\"ab-(naechsten-)?([a-z]+)--[^\"]*\"[^>]*?data-category-name=\"([^\"]+)\"");
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "montag", DayOfWeek.MONDAY, "dienstag", DayOfWeek.TUESDAY, "mittwoch", DayOfWeek.WEDNESDAY,
            "donnerstag", DayOfWeek.THURSDAY, "freitag", DayOfWeek.FRIDAY, "samstag", DayOfWeek.SATURDAY);
    // "(1 kg = 7.98)", "(1 l = 17.96)", "(100 g = 1.20)"
    private static final Pattern BASE_PRICE = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)?\\s*([A-Za-zÄÖÜäöü]+)\\s*=\\s*(\\d+(?:[.,]\\d+)?)");

    /** A Penny store and the price regions it belongs to this week and next week. */
    public record Market(String id, String name, String sellingRegion, String nextWeekSellingRegion) {
    }

    private final HttpFetcher http;
    private final ObjectMapper json = new ObjectMapper();

    public PennyClient(HttpFetcher http) {
        this.http = http;
    }

    public Market findMarket(String marketId) throws IOException {
        for (JsonNode m : json.readTree(http.get(BASE + "/.rest/market"))) {
            if (marketId.equals(m.path("wwIdent").asText())) {
                return new Market(marketId, text(m, "marketName"),
                        text(m, "sellingRegion"), text(m, "nextWeekSellingRegion"));
            }
        }
        throw new IOException("Penny store " + marketId + " not found in /.rest/market");
    }

    /** Offers of this week and (if already published) next week for the given store. */
    public OfferPull fetchOffers(String marketId) throws IOException {
        Market market = findMarket(marketId);
        if (market.sellingRegion() == null) {
            throw new IOException("Penny store " + marketId + " has no price region");
        }
        String page = http.get(BASE + "/angebote");
        List<Map<String, DayOfWeek>> starts = categoryStarts(page);
        Map<String, DayOfWeek> currentCategories = starts.get(0);
        Map<String, DayOfWeek> nextCategories = starts.get(1);
        if (currentCategories.isEmpty() && nextCategories.isEmpty()) {
            // No dated sections (layout changed?): every category in both weeks, Monday to Saturday.
            Matcher m = CATEGORY.matcher(page);
            while (m.find()) {
                currentCategories.put(m.group(1), DayOfWeek.MONDAY);
                nextCategories.put(m.group(1), DayOfWeek.MONDAY);
            }
        }
        if (currentCategories.isEmpty() && nextCategories.isEmpty()) {
            throw new IOException("no offer categories on penny.de/angebote (site layout changed?)");
        }
        String currentWeek = firstGroup(CURRENT_WEEK, page);
        if (currentWeek == null) {
            currentWeek = weekKey(LocalDate.now(JsonUtil.GERMANY));
        }
        String nextWeek = firstGroup(NEXT_WEEK, page);

        Map<String, StoreOffer> offers = new LinkedHashMap<>();   // key id|validFrom dedupes
        List<String> failed = new ArrayList<>();
        fetchWeek(currentWeek, market.sellingRegion(), currentCategories, offers, failed);
        if (nextWeek != null && market.nextWeekSellingRegion() != null) {
            fetchWeek(nextWeek, market.nextWeekSellingRegion(), nextCategories, offers, failed);
        }
        return new OfferPull(new ArrayList<>(offers.values()), failed);
    }

    /**
     * The offers page has one section per category and week, e.g.
     * {@code <section id="ab-donnerstag--obst-und-gemuese1" ... data-category-name="obst-und-gemuese1">}.
     * The id prefix is the day that category's offers start ("ab-montag" Mo-Sa, "ab-donnerstag" Do-Sa,
     * "ab-freitag" Fr-Sa for Framstag); "ab-naechsten-..." is next week. Returns [this week, next week],
     * each category -> start day. The page repeats the sections (menu), so the first one wins.
     */
    static List<Map<String, DayOfWeek>> categoryStarts(String page) {
        Map<String, DayOfWeek> current = new LinkedHashMap<>();
        Map<String, DayOfWeek> next = new LinkedHashMap<>();
        Matcher m = SECTION.matcher(page);
        while (m.find()) {
            DayOfWeek start = WEEKDAYS.getOrDefault(m.group(2), DayOfWeek.MONDAY);
            (m.group(1) == null ? current : next).putIfAbsent(m.group(3), start);
        }
        return List.of(current, next);
    }

    private void fetchWeek(String week, String region, Map<String, DayOfWeek> categories,
                           Map<String, StoreOffer> offers, List<String> failed) {
        LocalDate monday = weekStart(week);
        LocalDate saturday = monday.plusDays(5);
        for (Map.Entry<String, DayOfWeek> entry : categories.entrySet()) {
            String category = entry.getKey();
            LocalDate from = monday.with(entry.getValue());
            String url = BASE + "/.rest/offers/by-category/" + week + "/" + enc(category) + "?region=" + enc(region);
            JsonNode body;
            try {
                body = json.readTree(http.get(url));
            } catch (FileNotFoundException e) {
                continue;                               // category doesn't run that week
            } catch (IOException e) {
                failed.add(url + ": " + e.getMessage());
                continue;
            }
            for (JsonNode tile : body.path("offerTiles")) {
                StoreOffer offer = toOffer(tile, category, region, from, saturday);
                if (offer != null) {
                    offers.put(offer.externalId() + "|" + offer.validFrom(), offer);
                }
            }
        }
    }

    StoreOffer toOffer(JsonNode tile, String category, String region, LocalDate from, LocalDate until) {
        String title = text(tile, "title");
        Double sale = firstNumber(text(tile, "price"));          // may carry a footnote mark: "1.99²"
        if (title == null || sale == null) {
            return null;
        }
        title = title.replaceAll("\\*+$", "").strip();          // trailing footnote asterisk
        String id = text(tile, "uuid");
        if (id == null) {
            id = title;                                          // fallback keeps the row unique per week
        }
        Double normal = firstNumber(text(tile, "listPrice"));
        if (normal == null) {
            normal = firstNumber(text(tile, "crossOutPrice"));
        }
        if (normal == null) {
            normal = firstNumber(text(tile, "originalPrice"));   // the "offer" tile type, often an UVP
        }
        Double basePrice = null;
        String baseUnit = null;
        String baseText = text(tile, "basePrice");
        if (baseText != null) {
            Matcher b = BASE_PRICE.matcher(baseText);
            if (b.find()) {
                String amount = b.group(1);
                baseUnit = (amount == null || "1".equals(amount)) ? b.group(2) : amount + " " + b.group(2);
                basePrice = Double.valueOf(b.group(3).replace(',', '.'));
            }
        }
        String quantity = text(tile, "quantity");
        if (quantity != null) {
            quantity = quantity.replaceFirst("^je\\s+", "").strip();
        }
        String[] brandAndName = splitBrand(title);
        return new StoreOffer(id, brandAndName[0], brandAndName[1], quantity, sale,
                1, null,                                 // Penny tiles are single-unit; deposit not exposed
                normal, basePrice, baseUnit, null,
                text(tile, "advantage"), category, region, from, until);
    }

    /** "MÜHLENHOF Frisches Puten-Gulasch" -> {"MÜHLENHOF", "Frisches Puten-Gulasch"}; no caps prefix -> {null, title}. */
    static String[] splitBrand(String title) {
        String[] words = title.split("\\s+");
        int i = 0;
        while (i < words.length - 1 && isBrandWord(words[i])) {
            i++;
        }
        if (i == 0) {
            return new String[]{null, title};
        }
        return new String[]{
                String.join(" ", Arrays.copyOfRange(words, 0, i)),
                String.join(" ", Arrays.copyOfRange(words, i, words.length))};
    }

    private static boolean isBrandWord(String word) {
        return word.length() >= 2
                && word.chars().anyMatch(Character::isLetter)
                && word.equals(word.toUpperCase(Locale.GERMAN));
    }

    /** Penny week keys look like "2026-39" (ISO year and week). */
    static LocalDate weekStart(String weekKey) {
        String[] parts = weekKey.split("-");
        return LocalDate.of(Integer.parseInt(parts[0]), 1, 4)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, Integer.parseInt(parts[1]))
                .with(DayOfWeek.MONDAY);
    }

    static String weekKey(LocalDate day) {
        return day.get(IsoFields.WEEK_BASED_YEAR) + "-" + String.format("%02d", day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private static String firstGroup(Pattern pattern, String s) {
        Matcher m = pattern.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
