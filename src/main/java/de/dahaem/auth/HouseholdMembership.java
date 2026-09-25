package de.dahaem.auth;

/**
 * A household together with the role one user has in it (one household_member row, joined to
 * its household). Plain data, no logic.
 */
public record HouseholdMembership(int householdMemberId, Household household, int userId, HouseholdRole role) {
}
