package de.dahaem.stores;

import java.util.List;

/**
 * Outcome of one refresh flow ("weekly" / "monthly"): the summary of every step that returned
 * one, plus a message for every step that threw. {@link #allOk()} is what the CLI turns into
 * its exit code.
 */
public record RefreshSummary(String flow, List<ImportSummary> results, List<String> failures) {

    public boolean allOk() {
        return failures.isEmpty() && results.stream().allMatch(r -> "OK".equals(r.status()));
    }
}
