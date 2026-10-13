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
 *   ItemAnimalRunner [--all] [--out items.tsv]               propose animals + alcohol (read-only), write a TSV
 *   ItemAnimalRunner --apply reviewed.tsv [--commit]         store the CONFIDENT / reviewed rows
 * </pre>
 *
 * <p>Proposing only reads the catalogue. Items that are already classified (an animal stored, or the alcohol
 * flag set) stay out of the proposal unless {@code --all} is given. The TSV has the CERTAIN animals (the item IS
 * from them), the POSSIBLE animals (a generic item such as Salami or Gelatine that exists from several animals)
 * and an alcohol column (yes / no). Applying is a dry run unless {@code --commit} is given; with it all rows go
 * in one transaction. Rows marked REVIEW are never stored; to accept one, fix the columns and change its status
 * to REVIEWED (or OK). The database comes from DAHAEM_DB_URL / _USER / _PASSWORD (migration 0012 must be applied).
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
        boolean all = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--commit")) {
                commit = true;
            } else if (args[i].equals("--all")) {
                all = true;
            } else if (args[i].equals("--out") && i + 1 < args.length) {
                out = Path.of(args[++i]);
            } else if (args[i].equals("--apply") && i + 1 < args.length) {
                apply = Path.of(args[++i]);
            } else {
                usage();
            }
        }
        if ((apply != null && (out != null || all)) || (apply == null && commit)) {
            usage();
        }
        ItemAnimalService service = new ItemAnimalService(new ItemAnimalRepository());
        try {
            if (apply == null) {
                propose(service, out, all);
            } else {
                apply(service, apply, commit);
            }
        } catch (RuntimeException | IOException e) {
            System.err.println("ITEM ANIMALS FAILED, nothing was written: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void propose(ItemAnimalService service, Path out, boolean all) throws IOException {
        List<ItemAnimalService.Row> rows = service.propose(all);
        String tsv = ItemAnimalService.toTsv(rows);
        if (out == null) {
            OUT.print(tsv);
        } else {
            Files.writeString(out, tsv, StandardCharsets.UTF_8);
        }
        long confident = rows.stream().filter(r -> r.status().equals("CONFIDENT")).count();
        System.err.println(rows.size() + " items to classify" + (all ? " (including the classified ones)" : "") + ": " + confident + " CONFIDENT, "
                + (rows.size() - confident) + " REVIEW" + (out == null ? "" : " -> " + out));
    }

    private static void apply(ItemAnimalService service, Path file, boolean commit) throws IOException {
        List<ItemAnimalService.Row> rows = ItemAnimalService.parseTsv(Files.readString(file, StandardCharsets.UTF_8));
        ItemAnimalService.ApplyReport report = service.apply(rows, commit);
        OUT.println(commit ? "COMMITTED" : "DRY RUN (nothing written; add --commit to store)");
        OUT.println("rows applied (CONFIDENT / reviewed): " + report.rowsApplied()
                + " (of which without any animal: " + report.rowsWithoutAnimals() + ")");
        OUT.println("rows skipped (still REVIEW / unknown status): " + report.rowsSkippedReview());
        OUT.println("animals to add: " + report.animalsToAdd() + (commit ? ", added: " + report.animalsAdded() : "")
                + "; certainty changed on stored animals: " + report.certaintyChanges());
        OUT.println("items to mark as containing alcohol: " + report.alcoholToSet());
        report.warnings().forEach(w -> OUT.println("warning: " + w));
    }

    private static void usage() {
        System.err.println("""
                usage: ItemAnimalRunner [--all] [--out items.tsv]
                       ItemAnimalRunner --apply reviewed.tsv [--commit]
                  (no option)  propose animals (certain / possible) and alcohol for the items not classified yet
                               (read-only), TSV to stdout or --out
                  --all        propose the already classified items again too
                  --apply      store the rows marked CONFIDENT, REVIEWED or OK; dry run unless --commit
                  --commit     write, in one transaction""");
        System.exit(2);
    }
}
