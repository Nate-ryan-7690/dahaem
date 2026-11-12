package de.dahaem.auth;

import de.dahaem.testsupport.TestDatabase;
import de.dahaem.testsupport.TestHousehold;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The equipment a household does not have, on a throwaway MariaDB: nothing is missing until a household says so. */
class HouseholdEquipmentServiceTest {

    private HouseholdEquipmentService service;
    private int household;
    private int other;

    @BeforeEach
    void setUp() throws IOException {
        TestDatabase.reset();
        service = new HouseholdEquipmentService(new HouseholdEquipmentRepository());
        household = TestHousehold.createHousehold("Zuhause", null);
        other = TestHousehold.createHousehold("Nebenan", null);
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void aHouseholdHasEverythingUntilItSaysOtherwise() {
        assertEquals(List.of(new HouseholdEquipmentService.Status("thermomix", "Thermomix", true)), service.list(household));
        assertTrue(service.excludedRecipeTags(household).isEmpty());
    }

    @Test
    void sayingItHasNoThermomixExcludesTheRecipeTagOnlyForThatHousehold() {
        HouseholdEquipmentService.Status status = service.set(household, "thermomix", false);

        assertEquals(new HouseholdEquipmentService.Status("thermomix", "Thermomix", false), status);
        assertEquals(List.of("Thermomix"), service.excludedRecipeTags(household));
        assertEquals(false, service.list(household).get(0).has());
        assertTrue(service.excludedRecipeTags(other).isEmpty(), "the other household still has it");
        assertEquals(true, service.list(other).get(0).has());
    }

    @Test
    void sayingItAgainChangesNothingAndHavingItBringsItBack() {
        service.set(household, "thermomix", false);
        service.set(household, "thermomix", false);
        assertEquals(List.of("Thermomix"), service.excludedRecipeTags(household));

        service.set(household, "thermomix", true);
        service.set(household, "thermomix", true);
        assertTrue(service.excludedRecipeTags(household).isEmpty());
    }

    @Test
    void anUnknownEquipmentIsRefused() {
        assertThrows(NoSuchElementException.class, () -> service.set(household, "airfryer", false));
    }
}
