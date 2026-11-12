package de.dahaem.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.dahaem.auth.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * The household's head count, so the app can scale portions to the people it feeds. The household is named in the URL
 * and must be the acting user's own (anything else is a 404); any member of it may read and set the head count.
 */
@RestController
@RequestMapping("/api/households")
public class HouseholdController {

    private final AccountService accountService;

    public HouseholdController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    public HouseholdDto get(@PathVariable("id") int id, Caller caller) {
        requireOwn(id, caller);
        return accountService.findHousehold(id)
                .map(HouseholdDto::from)
                .orElseThrow(() -> new NotFoundException("No household with id " + id));
    }

    /**
     * Sets the head count and returns the household: {@code {"people": 4}}. {@code {"people": null}} takes the number away
     * again (the household is then "not set" and meals follow the recipe or the default). 400 when people is missing,
     * not a whole number or below 1; 404 for any household but the caller's own.
     */
    @PutMapping("/{id}/people")
    public HouseholdDto setPeople(@PathVariable("id") int id, @RequestBody JsonNode body, Caller caller) {
        requireOwn(id, caller);
        if (!body.isObject() || !body.has("people")) {
            throw new BadRequestException("'people' is required (a whole number, at least 1, or null to clear it)");
        }
        JsonNode node = body.get("people");
        Integer people;
        if (node.isNull()) {
            people = null;
        } else if (node.isInt()) {
            people = node.intValue();
        } else {
            throw new BadRequestException("'people' must be a whole number or null");
        }
        try {
            return HouseholdDto.from(accountService.setHouseholdPeople(id, people));
        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    /**
     * Renames the caller's household: {@code {"name": "Familie M"}} (1 to 100 characters). Admins only (403 NOT_ADMIN);
     * 404 for any household but the caller's own; 400 for an empty or too long name.
     */
    @PutMapping("/{id}/name")
    public HouseholdDto rename(@PathVariable("id") int id, @RequestBody JsonNode body, Caller caller) {
        requireOwn(id, caller);
        caller.requireAdmin();
        if (!body.isObject() || !body.hasNonNull("name") || !body.get("name").isTextual()) {
            throw new BadRequestException("'name' is required (text, 1 to " + AccountService.MAX_HOUSEHOLD_NAME + " characters)");
        }
        try {
            return HouseholdDto.from(accountService.renameHousehold(id, body.get("name").asText()));
        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    /** The caller's own household is the only one there is for them: any other id is not found, like an unknown one. */
    private static void requireOwn(int id, Caller caller) {
        if (id != caller.householdId()) {
            throw new NotFoundException("No household with id " + id);
        }
    }
}
