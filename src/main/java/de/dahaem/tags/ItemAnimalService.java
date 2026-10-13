package de.dahaem.tags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Proposes which animal(s) the meat / fish items of the catalogue come from, as a TSV for Nate to review,
 * and applies the reviewed TSV. No SQL here (see {@link ItemAnimalRepository}), no console I/O.
 */
public class ItemAnimalService {

    public static final String HEADER = "item_id\tname\tcategory\tsubcategory\tproposed_animals\tstatus";

    /** One TSV line. */
    public record Row(int itemId, String name, String category, String subcategory, List<String> animals,
                      String status) {
    }

    /** Result of {@link #apply}. */
    public record ApplyReport(boolean committed, int rowsApplied, int rowsWithoutAnimals, int rowsSkippedReview,
                              int animalsToAdd, int animalsAdded, List<String> warnings) {
    }

    private final ItemAnimalRepository repository;

    public ItemAnimalService(ItemAnimalRepository repository) {
        this.repository = repository;
    }

    /** The proposal for every meat / fish item that has no animal stored yet. Writes nothing. */
    public List<Row> propose() {
        Map<Integer, Set<String>> stored = repository.findAnimals();
        List<Row> rows = new ArrayList<>();
        for (CatalogItem item : repository.findCandidates()) {
            if (stored.containsKey(item.id())) {
                continue;   // already classified; this tool only adds
            }
            AnimalClassifier.Proposal proposal = AnimalClassifier.propose(item);
            rows.add(new Row(item.id(), item.name(), item.category(), item.subcategory(), proposal.animals(),
                    proposal.confidence().name()));
        }
        return rows;
    }

    public static String toTsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Row r : rows) {
            sb.append(r.itemId()).append('\t').append(clean(r.name())).append('\t').append(clean(r.category()))
                    .append('\t').append(clean(r.subcategory())).append('\t').append(String.join("+", r.animals()))
                    .append('\t').append(r.status()).append('\n');
        }
        return sb.toString();
    }

    /** Parses a TSV written by {@link #toTsv} (and edited by hand). Throws IllegalArgumentException on a bad line. */
    public static List<Row> parseTsv(String tsv) {
        List<Row> rows = new ArrayList<>();
        String[] lines = tsv.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || (i == 0 && line.startsWith("item_id"))) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols.length < 6) {
                throw new IllegalArgumentException("line " + (i + 1) + ": expected 6 tab-separated columns, got "
                        + cols.length);
            }
            int id;
            try {
                id = Integer.parseInt(cols[0].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": item id '" + cols[0] + "' is not a number");
            }
            List<String> animals = new ArrayList<>();
            for (String a : cols[4].split("[+,;/]")) {
                if (!a.isBlank()) {
                    animals.add(a.trim());
                }
            }
            rows.add(new Row(id, cols[1], cols[2], cols[3], animals, cols[5].trim()));
        }
        return rows;
    }

    /**
     * Stores the animals of the rows marked CONFIDENT, REVIEWED or OK; REVIEW rows (and anything else) are
     * skipped. Everything is validated first (animal names, item ids); a bad row aborts the whole run before
     * anything is written. Without {@code commit} nothing is written at all; with it the rows go in one
     * transaction. Only adds: stored animals are never changed or removed.
     */
    public ApplyReport apply(List<Row> rows, boolean commit) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<Integer, Set<String>> toStore = new LinkedHashMap<>();
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
            if (r.animals().isEmpty()) {
                withoutAnimals++;
                continue;
            }
            for (String animal : r.animals()) {
                if (!Animals.isAnimal(animal)) {
                    errors.add("item " + r.itemId() + " (" + r.name() + "): '" + animal + "' is not one of "
                            + Animals.ALL);
                }
            }
            toStore.computeIfAbsent(r.itemId(), k -> new LinkedHashSet<>()).addAll(r.animals());
        }
        Set<Integer> existing = repository.existingItemIds(toStore.keySet());
        for (int id : toStore.keySet()) {
            if (!existing.contains(id)) {
                errors.add("item " + id + " does not exist in the catalogue");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("nothing written, fix these rows first:\n  - "
                    + String.join("\n  - ", errors));
        }
        Map<Integer, Set<String>> stored = repository.findAnimals();
        int toAdd = 0;
        for (Map.Entry<Integer, Set<String>> e : toStore.entrySet()) {
            for (String animal : e.getValue()) {
                if (!stored.getOrDefault(e.getKey(), Set.of()).contains(animal)) {
                    toAdd++;
                }
            }
        }
        if (commit) {
            repository.insertAnimals(toStore);
        }
        return new ApplyReport(commit, applied, withoutAnimals, skipped, toAdd, commit ? toAdd : 0, warnings);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
