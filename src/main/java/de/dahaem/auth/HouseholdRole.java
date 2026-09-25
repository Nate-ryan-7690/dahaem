package de.dahaem.auth;

/** What a user may do in their household. Stored by name in household_member.role. */
public enum HouseholdRole {
    /** Created the household (the first user); will manage members once there is a UI for it. */
    ADMIN,
    /** Everyone else in the household. */
    MEMBER
}
