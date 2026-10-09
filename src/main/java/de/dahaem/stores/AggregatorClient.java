package de.dahaem.stores;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls weekly offers from the aggregator aktionspreis.de (SECONDARY source).
 *
 * It is used for the chains we have no clean direct feed for (Netto, Edeka, Rewe,
 * Lidl, famila) and, for ALDI, as a cross-check against the direct pull. The data
 * is second-hand (the aggregator re-typing each flyer), so every offer is tagged
 * source = AGGREGATOR downstream and ranked below a direct store pull.
 *
 * Page: https://www.aktionspreis.de/prospekt/<slug>-angebote . No bot protection;
 * plain HTML. Each offer is a `div.produkt_box` inside an `<a href="/angebote/...">`:
 *   - discount %  : span.percent_new  ("55%")   -> used to derive the old price
 *   - name+brand  : first <b> ("Arla Buko<br> versch. Sorten")
 *   - size        : the anchor title attribute ("Buko (200g)")
 *   - sale price  : the red <b> (color #c03938, "0,89 €")
 *   - base price  : "4,45 € je kg"
 *   - TIEFSTPREIS : lowest-price badge (kept in promoText)
 * There is no explicit old price and no Pfand on the tiles; the old price is
 * derived from the discount %. Offers only, no full range.
 *
 * Dates: the tiles have none; the page header gives the week ("Angebote vom 05.10. - 11.10.2026"),
 * which every offer on the page gets. Next week's flyer is at
 * /prospekte/<slug>-angebote-naechsteWoche once the site has it.
 */
public class AggregatorClient {

    private static final String BASE = "https://www.aktionspreis.de/prospekt/";
    private static final String NEXT_BASE = "https://www.aktionspreis.de/prospekte/";   // next week: plural path

    /**
     * CLI store key -> (display name EXACTLY as the row in the `store` table, aktionspreis url slug).
     * Display names must match the existing store rows or a duplicate store is created.
     * Only Nate's own stores that the aggregator actually serves are listed:
     *   E-center is not on aktionspreis (404; it is an Edeka sub-format);
     *   Futterhaus (das-futterhaus) and Rossman (rossmann) are listed there but return no parseable offers.
     */
    public static final Map<String, String[]> STORES = new LinkedHashMap<>() {{
        put("aldi",   new String[]{"Aldi", "aldi-nord"});  // cross-check against the direct pull
        put("lidl",   new String[]{"Lidl", "lidl"});
        put("edeka",  new String[]{"Edeka", "edeka"});
        put("rewe",   new String[]{"Rewe", "rewe"});
        put("famila", new String[]{"Famila", "famila"});
        put("holab",  new String[]{"Hol-Ab", "holab"});
    }};

    /** Chains that show more (or, for famila, any) offers once a PLZ is set. Others are national. */
    public static final Set<String> REGIONAL = Set.of("famila", "edeka", "rewe");

    private static final Pattern TILE = Pattern.compile(
            "<a\\s+([^>]*href=\"/angebote/[^\"]+\"[^>]*)>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern HREF    = Pattern.compile("href=\"(/angebote/[^\"]+)\"");
    private static final Pattern TITLE   = Pattern.compile("title=\"([^\"]*)\"");
    private static final Pattern PERCENT = Pattern.compile("percent_new\">\\s*(\\d+)\\s*%");
    private static final Pattern BOLD    = Pattern.compile("<b>(.*?)</b>", Pattern.DOTALL);
    private static final Pattern PRICE   = Pattern.compile("#c03938;?\">\\s*([0-9][0-9.,]*)");
    private static final Pattern BASE_P  = Pattern.compile(
            "([0-9][0-9.,]*)\\s*(?:&euro;|\\u20ac)\\s*je\\s*([A-Za-z\\u00e4]+)");
    private static final Pattern SIZE    = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern VALIDITY = Pattern.compile(
            "Angebote vom (\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})?\\s*-\\s*(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

    private final HttpFetcher http;

    public AggregatorClient(HttpFetcher http) {
        this.http = http;
    }

    public boolean knows(String storeKey) {
        return STORES.containsKey(storeKey);
    }

    public String displayName(String storeKey) {
        return STORES.get(storeKey)[0];
    }

    /**
     * Fetches and parses one chain's offers page. Single request for national chains;
     * for a regional chain (famila) it primes the session then POSTs the PLZ, which the
     * site remembers in the session and answers with the regional offers.
     * plz may be empty for national chains; it is required for a REGIONAL one.
     */
    public OfferPull fetchOffers(String storeKey, String plz) throws IOException {
        String[] s = STORES.get(storeKey);
        if (s == null) {
            throw new IllegalArgumentException("unknown aggregator store: " + storeKey);
        }
        String url = BASE + s[1] + "-angebote";
        String html;
        if (REGIONAL.contains(storeKey) && plz != null && !plz.isBlank()) {
            http.get(url);                                   // prime the PHPSESSID session
            html = http.post(url, Map.of("set_plz", plz.trim(), "plz_los", "LOS"));  // regional offers
        } else {
            html = http.get(url);                            // national page (famila without a PLZ shows nothing)
        }
        LocalDate monday = LocalDate.now(JsonUtil.GERMANY).with(DayOfWeek.MONDAY);
        List<StoreOffer> offers = new ArrayList<>(parse(html, monday));
        List<String> failed = new ArrayList<>();
        if (offers.isEmpty()) {
            failed.add(url + " (no tiles found)");
        }
        // Next week's flyer, once the site has it. Same session, so a regional chain keeps its PLZ.
        String nextUrl = NEXT_BASE + s[1] + "-angebote-naechsteWoche";
        try {
            offers.addAll(parse(http.get(nextUrl), monday.plusWeeks(1)));
        } catch (FileNotFoundException e) {
            // not published yet
        } catch (IOException e) {
            failed.add(nextUrl + ": " + e.getMessage());
        }
        return new OfferPull(offers, failed);
    }

    List<StoreOffer> parse(String html) {
        return parse(html, LocalDate.now(JsonUtil.GERMANY).with(DayOfWeek.MONDAY));
    }

    /**
     * Offers valid for the range in the page header ("Angebote vom 05.10. - 11.10.2026").
     * The tiles carry no dates of their own. Without a header the week of fallbackMonday,
     * Monday to Saturday, is used, so repeated pulls in one week update the same rows.
     */
    List<StoreOffer> parse(String html, LocalDate fallbackMonday) {
        List<StoreOffer> out = new ArrayList<>();
        LocalDate[] valid = validity(html, fallbackMonday);
        Matcher tile = TILE.matcher(html);
        while (tile.find()) {
            String attrs = tile.group(1);
            String inner = tile.group(2);
            String href = find(HREF, attrs, 1);
            if (href == null) {
                continue;
            }
            String externalId = href.substring(href.lastIndexOf('/') + 1);   // slug = stable id

            String name = null, variant = null;
            Matcher b = BOLD.matcher(inner);
            if (b.find()) {
                String[] parts = b.group(1).split("<br\\s*/?>", 2);
                name = clean(parts[0]);
                variant = parts.length > 1 ? clean(parts[1]) : null;
            }
            if (name == null || name.isEmpty()) {
                continue;                               // no product name: not a real tile
            }

            Double salePrice = num(find(PRICE, inner, 1));
            if (salePrice == null) {
                continue;                               // no price: skip
            }
            String title = find(TITLE, attrs, 1);
            String size = title == null ? null : find(SIZE, title, 1);

            Double basePrice = null;
            String baseUnit = null;
            Matcher bp = BASE_P.matcher(inner);
            if (bp.find()) {
                basePrice = num(bp.group(1));
                baseUnit = bp.group(2).toLowerCase();
            }

            Integer pct = intOrNull(find(PERCENT, inner, 1));
            Double normalPrice = (pct != null && pct > 0 && pct < 100)
                    ? Math.round(salePrice / (1.0 - pct / 100.0) * 100.0) / 100.0
                    : null;

            StringBuilder promo = new StringBuilder();
            if (pct != null) promo.append("-").append(pct).append("%");
            if (inner.contains("TIEFSTPREIS")) promo.append(promo.length() > 0 ? " " : "").append("TIEFSTPREIS");
            if (variant != null && !variant.isEmpty()) promo.append(promo.length() > 0 ? " " : "").append(variant);

            out.add(new StoreOffer(externalId, null, name, size, salePrice,
                    1, null, normalPrice, basePrice, baseUnit, null,
                    promo.length() == 0 ? null : promo.toString(),
                    "", "", valid[0], valid[1]));
        }
        return out;
    }

    /** [from, until] from "Angebote vom 05.10. - 11.10.2026"; the start may omit its year. */
    static LocalDate[] validity(String html, LocalDate fallbackMonday) {
        Matcher m = VALIDITY.matcher(html);
        if (!m.find()) {
            return new LocalDate[]{fallbackMonday, fallbackMonday.plusDays(5)};
        }
        int untilYear = Integer.parseInt(m.group(6));
        int fromMonth = Integer.parseInt(m.group(2));
        int untilMonth = Integer.parseInt(m.group(5));
        int fromYear = m.group(3) != null ? Integer.parseInt(m.group(3))
                : fromMonth > untilMonth ? untilYear - 1 : untilYear;    // "29.12. - 04.01.2027"
        return new LocalDate[]{
                LocalDate.of(fromYear, fromMonth, Integer.parseInt(m.group(1))),
                LocalDate.of(untilYear, untilMonth, Integer.parseInt(m.group(4)))};
    }

    // ---- small helpers (regex HTML parse; tiles are machine-generated and uniform) ----

    private static String find(Pattern p, String s, int group) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(group) : null;
    }

    private static String clean(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    /** German price text ("1.234,56") -> double. */
    private static Double num(String s) {
        if (s == null) return null;
        String t = s.replace(".", "").replace(",", ".").trim();
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOrNull(String s) {
        try {
            return s == null ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
