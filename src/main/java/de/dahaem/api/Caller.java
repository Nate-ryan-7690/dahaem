package de.dahaem.api;

import de.dahaem.auth.HouseholdRole;

/**
 * Who is asking: the logged-in user the PHP front end names in {@code X-Acting-User}, and the household that user belongs
 * to. A controller method that takes a {@code Caller} works on that household's data only; the household is never taken
 * from the request itself.
 */
public record Caller(int userId, int householdId, HouseholdRole role) {

    /** @throws NotAdminException (403 NOT_ADMIN) unless the caller is an admin of their household */
    public void requireAdmin() {
        if (role != HouseholdRole.ADMIN) {
            throw new NotAdminException();
        }
    }
}
