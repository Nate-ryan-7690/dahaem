package de.dahaem.receipts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command-line entry point for importing PENNY receipts as purchase history.
 *
 *   ReceiptImportRunner <folder> [--commit] [--overrides <file>]
 *   ReceiptImportRunner --rematch [--commit] [--overrides <file>]
 *
 * {@code <folder>} holds one {@code .txt} per receipt, made with {@code pdftotext -layout -enc UTF-8}.
 * Without {@code --commit} it is a dry run: it reads the catalog and the stored receipt keys and
 * prints the report, writing nothing. With {@code --commit} all new receipts are written in one
 * transaction. Overrides default to config/receipt_overrides.json (run from the app directory).
 * {@code --rematch} reads no folder: it re-runs the matcher on the lines already stored (match_source
 * NULL, 'rule' or 'override', never 'manual') and changes item, match source and brand where the result
 * differs ({@link ReceiptRematchService}); dry run by default, {@code --commit} = one transaction.
 * The database comes from DAHAEM_DB_URL / DAHAEM_DB_USER / DAHAEM_DB_PASSWORD.
 *
 * Exit code: 0 = OK, 1 = a receipt was skipped or the import failed (nothing written), 2 = bad arguments.
 */
public class ReceiptImportRunner {

    public static void main(String[] args) {
        Path folder = null;
        boolean commit = false;
        boolean rematch = false;
        Path overrides = Path.of("config", "receipt_overrides.json");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--commit")) {
                commit = true;
            } else if (args[i].equals("--rematch")) {
                rematch = true;
            } else if (args[i].equals("--overrides") && i + 1 < args.length) {
                overrides = Path.of(args[++i]);
            } else if (!args[i].startsWith("--") && folder == null) {
                folder = Path.of(args[i]);
            } else {
                usage();
                return;
            }
        }
        if (rematch) {
            if (folder != null) {
                usage();
                return;
            }
            try {
                ReceiptRematchService.ReceiptRematchReport report =
                        new ReceiptRematchService(ReceiptMatcher.Overrides.load(overrides)).run(commit);
                System.out.print(report.toText());
                System.exit(0);
            } catch (RuntimeException e) {
                System.err.println("RECEIPT REMATCH FAILED, nothing was written: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        if (folder == null || !Files.isDirectory(folder)) {
            usage();
            return;
        }
        try {
            List<ReceiptImportService.ReceiptFile> files = read(folder);
            if (files.isEmpty()) {
                System.err.println("no .txt receipts in " + folder);
                System.exit(2);
            }
            ReceiptImportReport report = new ReceiptImportService(ReceiptMatcher.Overrides.load(overrides))
                    .run(files, commit);
            System.out.print(report.toText());
            System.exit(report.allBalanced() ? 0 : 1);
        } catch (RuntimeException | IOException e) {
            System.err.println("RECEIPT IMPORT FAILED, nothing was written: " + e.getMessage());
            System.exit(1);
        }
    }

    static List<ReceiptImportService.ReceiptFile> read(Path folder) throws IOException {
        List<ReceiptImportService.ReceiptFile> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : files.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".txt")).sorted().toList()) {
                out.add(new ReceiptImportService.ReceiptFile(p.getFileName().toString(),
                        Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        return out;
    }

    private static void usage() {
        System.err.println("""
                usage: ReceiptImportRunner <folder> [--commit] [--overrides <file>]
                       ReceiptImportRunner --rematch [--commit] [--overrides <file>]
                  <folder>     one .txt per PENNY receipt (pdftotext -layout -enc UTF-8)
                  --rematch    re-match the stored lines (not 'manual') with the current overrides and rules
                  --commit     write the new receipts / the rematch changes (one transaction); default is a dry run
                  --overrides  receipt overrides file (default config/receipt_overrides.json)""");
        System.exit(2);
    }
}
