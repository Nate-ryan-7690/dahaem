package de.dahaem.api;

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

    /** Sets the head count and returns the household. 400 when people is missing or below 1, 404 for an unknown household. */
    @PutMapping("/{id}/people")
    public HouseholdDto setPeople(@PathVariable("id") int id, @RequestBody SetHouseholdPeopleRequest request,
                                  Caller caller) {
        requireOwn(id, caller);
        if (request.people() == null) {
            throw new BadRequestException("'people' is required (a whole number, at least 1)");
        }
        try {
            return HouseholdDto.from(accountService.setHouseholdPeople(id, request.people()));
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
