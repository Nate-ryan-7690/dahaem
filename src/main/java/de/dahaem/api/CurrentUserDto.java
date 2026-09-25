package de.dahaem.api;

import de.dahaem.auth.AppUser;
import de.dahaem.auth.HouseholdMembership;

/** Answer of GET /api/auth/me. household is null for a user who is in no household. The password hash is never sent. */
public record CurrentUserDto(int id, String email, String displayName, HouseholdDto household) {

    public record HouseholdDto(int id, String name, String role) {
    }

    static CurrentUserDto from(AppUser user, HouseholdMembership membership) {
        HouseholdDto household = membership == null ? null : new HouseholdDto(
                membership.household().getId(), membership.household().getName(), membership.role().name());
        return new CurrentUserDto(user.getId(), user.getEmail(), user.getDisplayName(), household);
    }
}
