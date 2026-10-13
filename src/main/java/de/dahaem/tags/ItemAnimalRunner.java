package de.dahaem.tags;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line tool for the item → animal table (inventory_item_animal).
 *
 * <pre>
 *   ItemAnimalRunner [--out items.tsv]                       propose animals (read-only), write a TSV
 *   ItemAnimalRunner --apply reviewed.tsv [--commit]         store the CONFIDENT / reviewed rows
 * </pre>
 *
 * <p>Proposing only reads the catalogue. Applying is a dry run unless {@code --commit} is given; with it
 * all rows go in one transaction. Rows marked REVIEW are never stored; to accept one, fix the animals column
 * and change its status to REVIEWED (or OK). The database comes from DAHAEM_DB_URL / _USER / _PASSWORD.
 *
 * Exit code: 0 = OK, 1 = failed (nothing written), 2 = bad arguments.
 */
public class ItemAnimalRunner {

    // UTF-8 whatever the console default is: the tag names and item names carry umlauts.
    private static final java.io.PrintStream OUT = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8);

    public static void main(String[] args) {
        Path out = null;
        Path apply = null;
        boolean commit = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--commit")) {
                commit = true;
            } else if (args[i].equals("--out") && i + 1 < args.length) {
                out = Path.of(args[++i]);
            } else if (args[i].equals("--apply") && i + 1 < args.length) {
                apply = Path.of(args[++i]);
            } else {
                usage();
            }
        }
        if ((apply != null && out != null) || (apply == null && commit)) {
            usage();
        }
        ItemAnimalService service = new ItemAnimalService(new ItemAnimalRepository());
        try {
            if (apply == null) {
                propose(service, out);
            } else {
                apply(service, apply, commit);
            }
        } catch (RuntimeException | IOException e) {
            System.err.println("ITEM ANIMALS FAILED, nothing was written: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void propose(ItemAnimalService service, Path out) throws IOException {
        List<ItemAnimalService.Row> rows = service.propose();
        String tsv = ItemAnimalService.toTsv(rows);
        if (out == null) {
            OUT.print(tsv);
        } else {
            Files.writeString(out, tsv, StandardCharsets.UTF_8);
        }
        long confident = rows.stream().filter(r -> r.status().equals("CONFIDENT")).count();
        System.err.println(rows.size() + " items without an animal: " + confident + " CONFIDENT, "
                + (rows.size() - confident) + " REVIEW" + (out == null ? "" : " -> " + out));
    }

    private static void apply(ItemAnimalService service, Path file, boolean commit) throws IOException {
        List<ItemAnimalService.Row> rows = ItemAnimalService.parseTsv(Files.readString(file, StandardCharsets.UTF_8));
        ItemAnimalService.ApplyReport report = service.apply(rows, commit);
        OUT.println(commit ? "COMMITTED" : "DRY RUN (nothing written; add --commit to store)");
        OUT.println("rows applied (CONFIDENT / reviewed): " + report.rowsApplied()
                + " (of which without any animal: " + report.rowsWithoutAnimals() + ")");
        OUT.println("rows skipped (still REVIEW / unknown status): " + report.rowsSkippedReview());
        OUT.println("animals to add: " + report.animalsToAdd() + (commit ? ", added: " + report.animalsAdded() : ""));
        report.warnings().forEach(w -> OUT.println("warning: " + w));
    }

    private static void usage() {
        System.err.println("""
                usage: ItemAnimalRunner [--out items.tsv]
                       ItemAnimalRunner --apply reviewed.tsv [--commit]
                  (no option)  propose animals for the meat / fish items without one (read-only), TSV to stdout or --out
                  --apply      store the rows marked CONFIDENT, REVIEWED or OK; dry run unless --commit
                  --commit     write, in one transaction""");
        System.exit(2);
    }
}
