package de.dahaem.auth;

/**
 * Result of {@link AccountService#createUser}: the new user, the household they were added to,
 * and whether that household was created for them (true for the very first user).
 */
public record CreatedAccount(AppUser user, HouseholdMembership membership, boolean newHousehold) {
}
