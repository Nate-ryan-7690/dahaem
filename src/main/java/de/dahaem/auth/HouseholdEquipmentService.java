package de.dahaem.auth;

import java.util.List;
import java.util.Set;

/**
 * Which kitchen equipment a household has. Everything is assumed to be there until the household says it is not
 * ({@link HouseholdEquipmentRepository}). No SQL here, no UI here.
 */
public class HouseholdEquipmentService {

    /** One piece of equipment as a household sees it. */
    public record Status(String code, String name, boolean has) {
    }

    private final HouseholdEquipmentRepository repository;

    public HouseholdEquipmentService(HouseholdEquipmentRepository repository) {
        this.repository = repository;
    }

    /** Every known equipment with whether the household has it. */
    public List<Status> list(int householdId) {
        Set<String> missing = repository.findMissing(householdId);
        return List.of(HouseholdEquipment.values()).stream()
                .map(e -> new Status(e.code(), e.displayName(), !missing.contains(e.code()))).toList();
    }

    /**
     * Says whether the household has the equipment and returns its new state.
     *
     * @throws java.util.NoSuchElementException for a code that is not known
     */
    public Status set(int householdId, String code, boolean has) {
        HouseholdEquipment equipment = HouseholdEquipment.byCode(code)
                .orElseThrow(() -> new java.util.NoSuchElementException("No equipment '" + code + "'"));
        repository.setHas(householdId, equipment.code(), has);
        return new Status(equipment.code(), equipment.displayName(), has);
    }

    /**
     * The names of the recipe tags a household does NOT want to see: those of the equipment it does not have. Used as hard
     * "exclude tags" rules in the recipe lists, the search and the suggestions. Empty when the household has everything.
     */
    public List<String> excludedRecipeTags(int householdId) {
        Set<String> missing = repository.findMissing(householdId);
        return List.of(HouseholdEquipment.values()).stream()
                .filter(e -> missing.contains(e.code())).map(HouseholdEquipment::recipeTag).toList();
    }
}
