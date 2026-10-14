package de.dahaem.tags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Proposes which animal(s) the meat / fish items of the catalogue come from, which generic items (Salami,
 * Gelatine ...) MAY come from which animals, and which items contain alcohol, as a TSV for Nate to review;
 * and applies the reviewed TSV. No SQL here (see {@link ItemAnimalRepository}), no console I/O.
 */
public class ItemAnimalService {

    public static final String HEADER = "item_id\tname\tcategory\tsubcategory\tcertain_animals\tpossible_animals\talcohol\tstatus";

    /**
     * One TSV line. {@code animals} are the certain animals (the item IS from them), {@code possibleAnimals}
     * the ones a generic item may be from; {@code alcohol} is the proposal that the item contains alcohol.
     */
    public record Row(int itemId, String name, String category, String subcategory, List<String> animals,
                      List<String> possibleAnimals, boolean alcohol, String status) {

        public Row(int itemId, String name, String category, String subcategory, List<String> animals,
                   String status) {
            this(itemId, name, category, subcategory, animals, List.of(), false, status);
        }
    }

    /** Result of {@link #apply}. */
    public record ApplyReport(boolean committed, int rowsApplied, int rowsWithoutAnimals, int rowsSkippedReview,
                              int animalsToAdd, int animalsAdded, int certaintyChanges, int alcoholToSet,
                              List<String> warnings) {
    }

    private final ItemAnimalRepository repository;

    public ItemAnimalService(ItemAnimalRepository repository) {
        this.repository = repository;
    }

    /**
     * The proposal for every item the classifier has something to say about (meat / fish items, generic animal
     * products, alcohol) that is not classified yet: no animal stored and no alcohol flag. With {@code all}
     * the classified items are proposed again. Writes nothing.
     */
    public List<Row> propose(boolean all) {
        Map<Integer, Set<String>> stored = repository.findAnimals();
        List<Row> rows = new ArrayList<>();
        for (CatalogItem item : repository.findAllItems()) {
            if (!all && (stored.containsKey(item.id()) || item.alcohol())) {
                continue;   // already classified; this tool only adds
            }
            boolean candidate = AnimalClassifier.isAnimalCandidate(item);
            Optional<AnimalClassifier.Confidence> alcohol = AnimalClassifier.proposeAlcohol(item);
            if (!candidate && alcohol.isEmpty()) {
                continue;
            }
            List<String> certain = List.of();
            List<String> possible = List.of();
            boolean confident = true;
            if (candidate) {
                AnimalClassifier.Proposal proposal = AnimalClassifier.propose(item);
                certain = proposal.animals();
                possible = proposal.possibleAnimals();
                confident = proposal.confidence() == AnimalClassifier.Confidence.CONFIDENT;
            }
            if (alcohol.isPresent() && alcohol.get() != AnimalClassifier.Confidence.CONFIDENT) {
                confident = false;
            }
            rows.add(new Row(item.id(), item.name(), item.category(), item.subcategory(), certain, possible,
                    alcohol.isPresent(), confident ? "CONFIDENT" : "REVIEW"));
        }
        return rows;
    }

    public List<Row> propose() {
        return propose(false);
    }

    public static String toTsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Row r : rows) {
            sb.append(r.itemId()).append('\t').append(clean(r.name())).append('\t').append(clean(r.category()))
                    .append('\t').append(clean(r.subcategory())).append('\t').append(String.join("+", r.animals()))
                    .append('\t').append(String.join("+", r.possibleAnimals()))
                    .append('\t').append(r.alcohol() ? "yes" : "no")
                    .append('\t').append(r.status()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Parses a TSV written by {@link #toTsv} (and edited by hand). The 6-column format of the first version
     * (item_id, name, category, subcategory, animals, status) is still read: all its animals are certain and
     * the item is not marked as alcohol. Throws IllegalArgumentException on a bad line.
     */
    public static List<Row> parseTsv(String tsv) {
        List<Row> rows = new ArrayList<>();
        String[] lines = tsv.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || (i == 0 && line.startsWith("item_id"))) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            boolean legacy = cols.length == 6;
            if (!legacy && cols.length < 8) {
                throw new IllegalArgumentException("line " + (i + 1) + ": expected 8 tab-separated columns, got "
                        + cols.length);
            }
            int id;
            try {
                id = Integer.parseInt(cols[0].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": item id '" + cols[0] + "' is not a number");
            }
            List<String> certain = animals(cols[4]);
            List<String> possible = legacy ? List.of() : animals(cols[5]);
            boolean alcohol = !legacy && parseYesNo(cols[6], i + 1);
            rows.add(new Row(id, cols[1], cols[2], cols[3], certain, possible, alcohol,
                    (legacy ? cols[5] : cols[7]).trim()));
        }
        return rows;
    }

    private static List<String> animals(String column) {
        List<String> animals = new ArrayList<>();
        for (String a : column.split("[+,;/]")) {
            if (!a.isBlank()) {
                animals.add(a.trim());
            }
        }
        return animals;
    }

    private static boolean parseYesNo(String value, int line) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "yes", "ja", "y", "1", "true" -> true;
            case "no", "nein", "n", "0", "false", "" -> false;
            default -> throw new IllegalArgumentException("line " + line + ": alcohol must be yes or no, got '"
                    + value + "'");
        };
    }

    /**
     * Stores the rows marked CONFIDENT, REVIEWED or OK; REVIEW rows (and anything else) are skipped. Everything
     * is validated first (animal names, an animal that is both certain and possible, item ids); a bad row aborts
     * the whole run before anything is written. Without {@code commit} nothing is written at all; with it the
     * rows go in one transaction. Animals are only added: an animal stored earlier is never removed (a row that
     * omits it only gets a warning), but its certainty follows the reviewed row. The alcohol flag is only set.
     */
    public ApplyReport apply(List<Row> rows, boolean commit) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<Integer, Map<String, String>> toStore = new LinkedHashMap<>();
        Set<Integer> alcoholItems = new LinkedHashSet<>();
        int withoutAnimals = 0;
        int skipped = 0;
        int applied = 0;
        for (Row r : rows) {
            String status = r.status().toUpperCase(Locale.ROOT);
            if (status.equals("REVIEW")) {
                skipped++;
                continue;
            }
            if (!(status.equals("CONFIDENT") || status.equals("REVIEWED") || status.equals("OK"))) {
                skipped++;
                warnings.add("item " + r.itemId() + " (" + r.name() + "): status '" + r.status()
                        + "' is not CONFIDENT / REVIEWED / OK, skipped");
                continue;
            }
            applied++;
            if (r.alcohol()) {
                alcoholItems.add(r.itemId());
            }
            if (r.animals().isEmpty() && r.possibleAnimals().isEmpty()) {
                withoutAnimals++;
                continue;
            }
            for (String animal : r.animals()) {
                checkAnimal(r, animal, errors);
            }
            for (String animal : r.possibleAnimals()) {
                checkAnimal(r, animal, errors);
                if (r.animals().contains(animal)) {
                    errors.add("item " + r.itemId() + " (" + r.name() + "): '" + animal
                            + "' is both certain and possible, keep one");
                }
            }
            Map<String, String> byAnimal = toStore.computeIfAbsent(r.itemId(), k -> new TreeMap<>());
            r.animals().forEach(a -> byAnimal.put(a, Animals.CERTAIN));
            r.possibleAnimals().forEach(a -> byAnimal.putIfAbsent(a, Animals.POSSIBLE));
        }
        Set<Integer> wanted = new LinkedHashSet<>(toStore.keySet());
        wanted.addAll(alcoholItems);
        Set<Integer> existing = repository.existingItemIds(wanted);
        for (int id : wanted) {
            if (!existing.contains(id)) {
                errors.add("item " + id + " does not exist in the catalogue");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("nothing written, fix these rows first:\n  - "
                    + String.join("\n  - ", errors));
        }
        Map<Integer, Map<String, String>> stored = repository.findAnimalCertainty();
        int toAdd = 0;
        int certaintyChanges = 0;
        for (Map.Entry<Integer, Map<String, String>> e : toStore.entrySet()) {
            Map<String, String> have = stored.getOrDefault(e.getKey(), Map.of());
            for (Map.Entry<String, String> animal : e.getValue().entrySet()) {
                String current = have.get(animal.getKey());
                if (current == null) {
                    toAdd++;
                } else if (!current.equals(animal.getValue())) {
                    certaintyChanges++;
                }
            }
            for (String animal : have.keySet()) {
                if (!e.getValue().containsKey(animal)) {
                    warnings.add("item " + e.getKey() + ": '" + animal + "' is stored but not in the reviewed row; "
                            + "left as it is (animals are never removed here)");
                }
            }
        }
        Set<Integer> alcoholNew = new LinkedHashSet<>(alcoholItems);
        repository.findAllItems().stream().filter(CatalogItem::alcohol).forEach(i -> alcoholNew.remove(i.id()));
        if (commit) {
            repository.store(toStore, alcoholItems);
        }
        return new ApplyReport(commit, applied, withoutAnimals, skipped, toAdd, commit ? toAdd : 0, certaintyChanges,
                alcoholNew.size(), warnings);
    }

    private static void checkAnimal(Row r, String animal, List<String> errors) {
        if (!Animals.isAnimal(animal)) {
            errors.add("item " + r.itemId() + " (" + r.name() + "): '" + animal + "' is not one of " + Animals.ALL);
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
