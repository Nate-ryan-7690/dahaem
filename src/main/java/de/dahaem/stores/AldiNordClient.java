package de.dahaem.stores;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.dahaem.stores.JsonUtil.date;
import static de.dahaem.stores.JsonUtil.number;
import static de.dahaem.stores.JsonUtil.text;

/**
 * Reads ALDI Nord's public website. The site is a Next.js app: every page embeds
 * its data as JSON in &lt;script id="__NEXT_DATA__"&gt;.
 *
 *  - Weekly offers: pageProps.apiData (itself a JSON string) holds an "OFFER_GET"
 *    call whose result "algoliaDataMap" maps product id -> product. The preview
 *    page carries next week's offers the same way.
 *  - Full range: every /sortiment/... category page embeds its product list in
 *    pageProps.algoliaState.initialResults (up to 1000 products per page).
 *
 * No API key, no login: we fetch the same pages a browser does.
 */
public class AldiNordClient {

    private static final String BASE = "https://www.aldi-nord.de";
    private static final List<String> OFFER_PAGES = List.of("/angebote.html", "/angebote-vorschau.html");
    private static final Pattern NEXT_DATA = Pattern.compile(
            "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);

    private final HttpFetcher http;
    private final ObjectMapper json = new ObjectMapper();

    public AldiNordClient(HttpFetcher http) {
        this.http = http;
    }

    // ---------------------------------------------------------------- offers

    /** This week's offers plus next week's preview. */
    public OfferPull fetchOffers() {
        Map<String, StoreOffer> offers = new LinkedHashMap<>();   // key id|validFrom dedupes across both pages
        List<String> failed = new ArrayList<>();
        for (String page : OFFER_PAGES) {
            try {
                JsonNode nextData = nextData(http.get(BASE + page));
                for (JsonNode call : apiCalls(nextData)) {
                    if (!"OFFER_GET".equals(call.path(0).asText())) {
                        continue;
                    }
                    for (JsonNode product : call.path(1).path("res").path("algoliaDataMap")) {
                        for (StoreOffer offer : toOffers(product)) {
                            offers.put(offer.externalId() + "|" + offer.validFrom(), offer);
                        }
                    }
                }
            } catch (IOException e) {
                failed.add(page + ": " + e.getMessage());
            }
        }
        return new OfferPull(new ArrayList<>(offers.values()), failed);
    }

    private List<StoreOffer> toOffers(JsonNode p) {
        String id = p.path("objectID").asText();
        String brand = text(p, "brandName");
        String name = text(p, "name");
        String unit = text(p, "salesUnit");
        String category = text(p, "mainCategoryID");
        Double deposit = number(p.path("depositValue"));   // deposit is a product field, constant across offers
        List<StoreOffer> out = new ArrayList<>();
        for (JsonNode promo : p.path("promotionPrices")) {
            addOffer(out, id, brand, name, unit, category, deposit, promo,
                    date(promo, "validFromLocalDate", "validFrom"),
                    date(promo, "validUntilLocalDate", "validUntil"));
        }
        if (out.isEmpty()) {                          // offer without a promotion block: use the current price
            JsonNode price = p.path("currentPrice");
            addOffer(out, id, brand, name, unit, category, deposit, price,
                    date(price, "validFromLocalDate", "validFrom"),
                    date(price, "validUntilLocalDate", "validUntil"));
        }
        return out;
    }

    private void addOffer(List<StoreOffer> out, String id, String brand, String name, String unit,
                          String category, Double deposit, JsonNode price, LocalDate from, LocalDate until) {
        Double sale = number(price.path("priceValue"));
        if (name == null || sale == null || from == null || until == null) {
            return;
        }
        JsonNode base = price.path("basePrice").path(0);
        unit = deriveSalesUnit(unit, sale, number(base.path("basePriceValue")), text(base, "basePriceScale"));
        out.add(new StoreOffer(id, brand, name, unit, sale,
                1, null,                                 // ALDI offers are single-unit, no multi-buy deal
                number(price.path("strikePrice").path("strikePriceValue")),
                number(base.path("basePriceValue")), text(base, "basePriceScale"),
                deposit,
                text(price.path("priceTagLabels"), "promoText1"),
                category, "", from, until));
    }

    private static final java.util.Set<String> CONTAINER_WORDS = java.util.Set.of(
            "packung", "beutel", "becher", "dose", "glas", "flasche", "schale", "netz", "karton", "tüte", "box", "tray");

    /**
     * ALDI sometimes gives only a container word as the pack size ("Packung") but always the price per kg or l.
     * Price / base price is the content: 2.99 EUR at 7.85 EUR/kg is 380 g. The result is rounded to the nearest
     * 5 g (10 g above 1 kg), the precision the cent-rounded base price supports, and keeps the container word:
     * "380-g-Packung". A pack size that already states a quantity is returned unchanged.
     */
    static String deriveSalesUnit(String unit, Double sale, Double basePrice, String baseScale) {
        if (unit == null || !CONTAINER_WORDS.contains(unit.trim().toLowerCase(java.util.Locale.GERMAN))
                || sale == null || basePrice == null || basePrice <= 0 || baseScale == null) {
            return unit;
        }
        String scale = baseScale.trim().toLowerCase(java.util.Locale.GERMAN);
        if (!scale.equals("kg") && !scale.equals("l")) {
            return unit;
        }
        double small = sale / basePrice * 1000;                  // g or ml
        long step = small >= 1000 ? 10 : 5;
        long rounded = Math.round(small / step) * step;
        return rounded > 0 ? rounded + "-" + (scale.equals("kg") ? "g" : "ml") + "-" + unit.trim() : unit;
    }

    // ---------------------------------------------------------------- full range

    /**
     * Walks the category tree from /sortiment.html downwards and collects every
     * product, deduped by ALDI's product id. Pages that fail are recorded, not fatal.
     */
    public CrawlResult fetchRange() {
        Deque<String> queue = new ArrayDeque<>(List.of("/sortiment.html"));
        Set<String> seen = new HashSet<>(queue);
        Map<String, StoreProduct> products = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        List<String> truncated = new ArrayList<>();
        int pages = 0;

        while (!queue.isEmpty()) {
            String path = queue.poll();
            String html;
            JsonNode nextData;
            try {
                html = http.get(BASE + path);
                nextData = nextData(html);
            } catch (IOException e) {
                failed.add(path);
                System.err.println("  FAILED " + path + ": " + e.getMessage());
                continue;
            }
            pages++;

            // queue every /sortiment/... category path from the page's apiData JSON (child categories);
            // the seen-set makes the BFS cover the whole tree without repeating a page.
            for (String child : categoryPaths(nextData)) {
                String childHtml = child + ".html";
                if (seen.add(childHtml)) {
                    queue.add(childHtml);
                }
            }

            int onPage = 0;
            int reported = 0;
            for (JsonNode result : algoliaResults(nextData)) {
                reported += result.path("nbHits").asInt(0);
                for (JsonNode hit : result.path("hits")) {
                    StoreProduct product = toProduct(hit);
                    products.putIfAbsent(product.externalId(), product);
                    onPage++;
                }
            }
            if (reported > onPage) {
                truncated.add(path + " (" + onPage + " of " + reported + ")");
            }
            System.out.printf("  [%3d] %-75s %4d products, %5d unique%n", pages, path, onPage, products.size());
        }
        return new CrawlResult(new ArrayList<>(products.values()), pages, failed, truncated);
    }

    private StoreProduct toProduct(JsonNode p) {
        JsonNode price = p.path("currentPrice");
        Double value = number(price.path("priceValue"));
        Double strike = number(price.path("strikePrice").path("strikePriceValue"));
        boolean onPromotion = p.path("promotionPrices").size() > 0;
        JsonNode base = price.path("basePrice").path(0);
        String slug = text(p, "productSlug");
        String description = text(p, "longDescription");
        return new StoreProduct(
                p.path("objectID").asText(),
                text(p, "brandName"),
                text(p, "name"),
                text(p, "salesUnit"),
                strike != null ? strike : value,       // during a promotion the crossed-out price is the normal one
                onPromotion && strike == null,
                number(base.path("basePriceValue")),
                text(base, "basePriceScale"),
                number(p.path("depositValue")),
                text(p, "mainCategoryID"),
                description,
                slug == null ? null : BASE + "/produkt/" + slug + ".html",
                p.path("isAvailable").asBoolean(true),
                VariantParser.parse(description));
    }

    // ---------------------------------------------------------------- page JSON

    private JsonNode nextData(String html) throws IOException {
        Matcher m = NEXT_DATA.matcher(html);
        if (!m.find()) {
            throw new IOException("page has no __NEXT_DATA__ (site layout changed?)");
        }
        return json.readTree(m.group(1));
    }

    /** pageProps.apiData: a JSON string holding [[callName, {req, res}], ...]. */
    private List<JsonNode> apiCalls(JsonNode nextData) throws IOException {
        JsonNode raw = nextData.path("props").path("pageProps").path("apiData");
        List<JsonNode> calls = new ArrayList<>();
        if (raw.isMissingNode() || raw.isNull()) {
            return calls;
        }
        (raw.isTextual() ? json.readTree(raw.asText()) : raw).forEach(calls::add);
        return calls;
    }

    /** Child /sortiment/... category paths from the page's apiData (the CHILDREN_GET call's reference.path values). */
    private Set<String> categoryPaths(JsonNode nextData) {
        Set<String> paths = new LinkedHashSet<>();
        try {
            for (JsonNode call : apiCalls(nextData)) {
                collectSortimentPaths(call, paths);
            }
        } catch (IOException e) {
            System.err.println("  could not read category children: " + e.getMessage());
        }
        return paths;
    }

    private void collectSortimentPaths(JsonNode node, Set<String> out) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                if ("path".equals(e.getKey()) && v.isTextual() && v.asText().startsWith("/sortiment/")) {
                    out.add(v.asText());
                } else {
                    collectSortimentPaths(v, out);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectSortimentPaths(child, out));
        }
    }

    /** The search-result blocks embedded on a category page (empty on pages without products). */
    private List<JsonNode> algoliaResults(JsonNode nextData) {
        JsonNode pageProps = nextData.path("props").path("pageProps");
        JsonNode initial = pageProps.path("algoliaState").path("initialResults");
        String index = pageProps.path("algoliaConfig").path("indexName").asText("");
        List<JsonNode> results = new ArrayList<>();
        if (initial.has(index)) {
            initial.get(index).path("results").forEach(results::add);
        } else {
            initial.forEach(block -> block.path("results").forEach(results::add));
        }
        return results;
    }
}
