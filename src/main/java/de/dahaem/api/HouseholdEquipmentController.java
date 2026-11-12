package de.dahaem.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.dahaem.auth.HouseholdEquipmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/** What kitchen equipment the caller's household has. Recipes that need equipment it lacks are left out of its lists. */
@RestController
public class HouseholdEquipmentController {

    private final HouseholdEquipmentService equipment;

    public HouseholdEquipmentController(HouseholdEquipmentService equipment) {
        this.equipment = equipment;
    }

    /**
     * Every kind of equipment the app knows ({@code code}, {@code name}) with whether the household has it ({@code has},
     * true until the household says otherwise). Today: {@code thermomix}.
     */
    @GetMapping("/api/household/equipment")
    public List<HouseholdEquipmentService.Status> list(Caller caller) {
        return equipment.list(caller.householdId());
    }

    /**
     * Says whether the household has this equipment: {@code {"has": false}}. With {@code false} the recipe lists, the search
     * and the suggestions leave out the recipes that need it (for the Thermomix: the recipes tagged "Thermomix"); with
     * {@code true} they come back. Any member of the household may do this. 200 with the new state; 404 for an unknown
     * code; 400 when {@code has} is not true or false.
     */
    @PutMapping("/api/household/equipment/{code}")
    public HouseholdEquipmentService.Status set(@PathVariable("code") String code, @RequestBody JsonNode body, Caller caller) {
        if (!body.isObject() || !body.has("has") || !body.get("has").isBoolean()) {
            throw new BadRequestException("'has' is required (true or false)");
        }
        try {
            return equipment.set(caller.householdId(), code, body.get("has").asBoolean());
        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        }
    }
}
