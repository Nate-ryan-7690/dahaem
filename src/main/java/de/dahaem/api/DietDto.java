package de.dahaem.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dahaem.diet.DietVerdict;
import de.dahaem.tags.RecipeFacts;

import java.util.List;

/**
 * What the diet rules say about a recipe, present only when rules were passed.
 * {@code status}: OK, ANPASSBAR (excluded only through POSSIBLE animals of generic items: see {@code hints}),
 * UNCLEAR (a line of the recipe was not matched and a hard rule applies; only returned with includeUnclear) or
 * HIDDEN (excluded; returned only by {@code GET /api/recipes/{id}}, never in the list).
 * {@code hints}: per item that may come from an excluded animal, the animals it may still be ({@code allowedAnimals},
 * e.g. Salami → Rind, Geflügel). {@code softHits}: what a soft rule (avoidTags / avoidItems) matched; absent when nothing.
 * {@code unmatchedIngredients}: the recipe's lines without a catalogue item (detail only).
 * Presets filter INGREDIENTS only; this is not a halal or kosher certification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DietDto(String status, List<Hint> hints, SoftHits softHits, List<Unmatched> unmatchedIngredients) {

    public record Hint(int itemId, String name, List<String> allowedAnimals, List<String> possibleAnimals) {

        static Hint from(DietVerdict.Hint hint) {
            return new Hint(hint.itemId(), hint.name(), hint.allowedAnimals(), hint.possibleAnimals());
        }
    }

    public record Item(int itemId, String name) {
    }

    public record SoftHits(List<String> tags, List<Item> items, List<Hint> possibleItems) {
    }

    public record Unmatched(Integer lineNo, String rawText, String reason) {
    }

    static DietDto from(DietVerdict verdict, List<RecipeFacts.Unmatched> unmatched) {
        DietVerdict.SoftHits soft = verdict.hasSoftHits() ? verdict.soft() : null;
        return new DietDto(verdict.status().name(), verdict.hints().stream().map(Hint::from).toList(),
                soft == null ? null : new SoftHits(soft.tags(),
                        soft.items().stream().map(i -> new Item(i.itemId(), i.name())).toList(),
                        soft.possibleItems().stream().map(Hint::from).toList()),
                unmatched == null ? null : unmatched.stream()
                        .map(u -> new Unmatched(u.lineNo(), u.rawText(), u.reason())).toList());
    }
}
