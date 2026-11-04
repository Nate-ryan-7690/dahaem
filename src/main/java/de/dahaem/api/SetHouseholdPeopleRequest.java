package de.dahaem.api;

/** Body of PUT /api/households/{id}/people. people is boxed so a missing field is a 400, not a 0. */
public record SetHouseholdPeopleRequest(Integer people) {
}
