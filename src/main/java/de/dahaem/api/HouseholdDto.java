package de.dahaem.api;

import de.dahaem.auth.Household;

import java.time.LocalDate;

/** A household as the API shows it. people is the head count and null until someone has set it. */
public record HouseholdDto(int id, String name, Integer people, LocalDate createdDate) {

    public static HouseholdDto from(Household household) {
        return new HouseholdDto(household.getId(), household.getName(), household.getPeople(),
                household.getCreatedDate());
    }
}
