package de.dahaem.tags;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Command-line tool that derives recipe tags from DAHÄM's own data.
 *
 * <pre>
 *   RecipeTagRunner [--commit] [--config config/recipe_tags.properties]
 *   RecipeTagRunner --animal-products
 * </pre>
 *
 * <p>Without {@code --commit} it is a dry run: it prints how many recipes would carry each tag, with
 * 10 example recipes per tag, and writes nothing. With {@code --commit} the changes go in one transaction;
 * only recipe_tag rows with source 'derived' are ever replaced, manual tags stay. {@code --animal-products}
 * only prints which catalogue items block vegetarisch / vegan, and why. The database comes from
 * DAHAEM_DB_URL / _USER / _PASSWORD.
 *
 * Exit code: 0 = OK, 1 = failed (nothing written), 2 = bad arguments.
 */
public class RecipeTagRunner {

    // UTF-8 whatever the console default is: the tag names and item names carry umlauts.
    private static final java.io.PrintStream OUT = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8);

    public static void main(String[] args) {
        boolean commit = false;
        boolean listProducts = false;
        Path config = Path.of("config", "recipe_tags.properties");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--commit")) {
                commit = true;
            } else if (args[i].equals("--animal-products")) {
                listProducts = true;
            } else if (args[i].equals("--config") && i + 1 < args.length) {
                config = Path.of(args[++i]);
            } else {
                usage();
            }
        }
        if (listProducts && commit) {
            usage();
        }
        try {
            RecipeTagService service = new RecipeTagService(new RecipeTagRepository(), TagSettings.load(config));
            if (listProducts) {
                for (Map.Entry<String, List<CatalogItem>> e : service.animalProducts().entrySet()) {
                    OUT.println("## " + e.getKey() + " (" + e.getValue().size() + ")");
                    e.getValue().forEach(i -> OUT.println("  " + i.id() + "\t" + i.name() + "\t" + i.category()
                            + (i.subcategory().isEmpty() ? "" : " / " + i.subcategory())));
                }
                return;
            }
            RecipeTagService.Plan plan = service.plan();
            if (commit) {
                plan = service.commit(plan);
            }
            OUT.print(plan.toText());
        } catch (RuntimeException e) {
            System.err.println("RECIPE TAGS FAILED, nothing was written: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usage() {
        System.err.println("""
                usage: RecipeTagRunner [--commit] [--config <file>]
                       RecipeTagRunner --animal-products
                  --commit           write the derived tags (one transaction); default is a dry run
                  --config           thresholds file (default config/recipe_tags.properties; missing = defaults)
                  --animal-products  list the catalogue items that block vegetarisch / vegan, with the reason""");
        System.exit(2);
    }
}
