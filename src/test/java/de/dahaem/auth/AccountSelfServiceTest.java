package de.dahaem.auth;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Changing one's own email and password on a throwaway MariaDB. */
class AccountSelfServiceTest {

    private static final String EMAIL = "you@example.com";
    private static final String PASSWORD = "correct horse battery";
    private static final String OTHER = "anna@example.de";

    private final AtomicLong clock = new AtomicLong();
    private AccountService accounts;
    private AccountSelfService self;
    private AppUser user;

    @BeforeEach
    void setUp() throws IOException {
        TestDatabase.reset();
        accounts = new AccountService(new AccountRepository(), new BCryptPasswordEncoder(4));
        accounts.createUser(EMAIL, "Nate", PASSWORD, "Zuhause", LocalDate.of(2026, 9, 26));
        accounts.createUser(OTHER, "Anna", "another long password", null, LocalDate.of(2026, 9, 27));
        self = new AccountSelfService(accounts, new LoginThrottle(clock::get));
        user = accounts.findByEmail(EMAIL).orElseThrow();
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void thePasswordCanBeChangedWithTheCurrentOne() {
        self.changePassword(user, PASSWORD, "a brand new password", "1.1.1.1");

        assertFalse(accounts.verifyPassword(EMAIL, PASSWORD).isPresent(), "the old password no longer works");
        assertTrue(accounts.verifyPassword(EMAIL, "a brand new password").isPresent());
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        assertThrows(InvalidCredentialsException.class,
                () -> self.changePassword(user, "not the password", "a brand new password", "1.1.1.1"));

        assertTrue(accounts.verifyPassword(EMAIL, PASSWORD).isPresent());
        assertFalse(accounts.verifyPassword(EMAIL, "a brand new password").isPresent());
    }

    @Test
    void aBadNewPasswordIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> self.changePassword(user, PASSWORD, "short", "1.1.1.1"));
        assertThrows(IllegalArgumentException.class, () -> self.changePassword(user, PASSWORD, "x".repeat(73), "1.1.1.1"));
        assertThrows(IllegalArgumentException.class, () -> self.changePassword(user, PASSWORD, PASSWORD, "1.1.1.1"),
                "the same password again is not a change");
        assertThrows(IllegalArgumentException.class, () -> self.changePassword(user, PASSWORD, null, "1.1.1.1"));
        assertTrue(accounts.verifyPassword(EMAIL, PASSWORD).isPresent());
    }

    @Test
    void theEmailCanBeChangedAndIsNormalised() {
        AppUser updated = self.changeEmail(user, PASSWORD, "  Nate.Real@Mail.DE ", "1.1.1.1");

        assertEquals("nate.real@mail.de", updated.getEmail());
        assertEquals(user.getId(), updated.getId());
        assertTrue(accounts.verifyPassword("nate.real@mail.de", PASSWORD).isPresent(), "log in with the new address");
        assertFalse(accounts.verifyPassword(EMAIL, PASSWORD).isPresent(), "the old address is gone");
    }

    @Test
    void anEmailThatIsInvalidOrTakenIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> self.changeEmail(user, PASSWORD, "not an email", "1.1.1.1"));
        assertThrows(IllegalArgumentException.class, () -> self.changeEmail(user, PASSWORD, "", "1.1.1.1"));
        assertThrows(EmailTakenException.class, () -> self.changeEmail(user, PASSWORD, OTHER, "1.1.1.1"));
        assertThrows(EmailTakenException.class, () -> self.changeEmail(user, PASSWORD, "ANNA@example.de", "1.1.1.1"));
        assertEquals(EMAIL, accounts.findById(user.getId()).orElseThrow().getEmail());
    }

    @Test
    void theSameEmailAgainIsNotAnError() {
        assertEquals(EMAIL, self.changeEmail(user, PASSWORD, EMAIL, "1.1.1.1").getEmail());
    }

    @Test
    void wrongPasswordsHereAreSlowedDownLikeLoginAttempts() {
        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> self.changeEmail(user, "wrong password " + 1, "x@y.de", "9.9.9.9"));
        }
        TooManyAttemptsException blocked = assertThrows(TooManyAttemptsException.class,
                () -> self.changeEmail(user, PASSWORD, "x@y.de", "9.9.9.9"));
        assertEquals(30, blocked.retryAfterSeconds());
        assertEquals(EMAIL, accounts.findById(user.getId()).orElseThrow().getEmail(), "nothing changed");
    }
}
