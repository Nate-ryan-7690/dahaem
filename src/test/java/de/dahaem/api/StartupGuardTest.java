package de.dahaem.api;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The API refuses to start without its secrets, and never prints their values. */
class StartupGuardTest {

    private static final Map<String, String> FULL = Map.of(
            "DAHAEM_DB_URL", "jdbc:mariadb://db:3306/x", "DAHAEM_DB_USER", "u", "DAHAEM_DB_PASSWORD", "hunter2-secret");

    private static UnaryOperator<String> env(Map<String, String> values) {
        return values::get;
    }

    @Test
    void startsWhenEverythingIsSet() {
        assertDoesNotThrow(() -> StartupGuard.check(true, "a-long-token", env(FULL)));
    }

    @Test
    void aMissingOrBlankTokenStopsTheStart() {
        for (String token : new String[]{null, "", "   "}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> StartupGuard.check(true, token, env(FULL)));
            assertTrue(e.getMessage().contains("INTERNAL_API_TOKEN"), e.getMessage());
        }
    }

    @Test
    void missingOrBlankDatabaseSettingsStopTheStartAndAreNamedTogether() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> StartupGuard.check(true, "t", env(Map.of("DAHAEM_DB_URL", " ", "DAHAEM_DB_USER", "u"))));
        assertTrue(e.getMessage().contains("DAHAEM_DB_URL"));
        assertTrue(e.getMessage().contains("DAHAEM_DB_PASSWORD"));
        assertFalse(e.getMessage().contains("DAHAEM_DB_USER"), "a variable that is set is not named");
    }

    @Test
    void theMessageNeverContainsASecretValue() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> StartupGuard.check(true, "", env(FULL)));
        assertFalse(e.getMessage().contains("hunter2-secret"));
        assertFalse(e.getMessage().contains("jdbc:"));
    }

    @Test
    void theCheckCanBeSwitchedOffForLocalDevelopment() {
        assertDoesNotThrow(() -> StartupGuard.check(false, null, env(Map.of())));
    }
}
