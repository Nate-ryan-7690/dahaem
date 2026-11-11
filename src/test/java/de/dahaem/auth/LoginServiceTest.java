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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checking a login for the PHP front end on a throwaway MariaDB: the right answer, the same wrong answer, the delay. */
class LoginServiceTest {

    private static final String EMAIL = "anna@example.de";
    private static final String PASSWORD = "correct horse battery";

    private final AtomicLong clock = new AtomicLong();
    private AccountService accounts;
    private LoginService login;

    @BeforeEach
    void setUp() throws IOException {
        TestDatabase.reset();
        accounts = new AccountService(new AccountRepository(), new BCryptPasswordEncoder(4));
        accounts.createUser(EMAIL, "Anna", PASSWORD, "Zuhause", LocalDate.of(2026, 9, 25));
        login = new LoginService(accounts, new LoginThrottle(clock::get));
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void theRightPasswordGivesTheUserAndTheirHousehold() {
        LoginService.Verified v = login.verify("  ANNA@example.de ", PASSWORD, "1.1.1.1");

        assertEquals(EMAIL, v.user().getEmail());
        assertEquals("Anna", v.user().getDisplayName());
        assertNotNull(v.membership());
        assertEquals("Zuhause", v.membership().household().getName());
        assertEquals(HouseholdRole.ADMIN, v.membership().role());
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailAreTheSameException() {
        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class,
                () -> login.verify(EMAIL, "not the password", "1.1.1.1"));
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> login.verify("nobody@example.de", PASSWORD, "1.1.1.1"));

        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void fiveFailuresSlowTheAddressDownEvenForTheRightPasswordUntilTheDelayIsOver() {
        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidCredentialsException.class, () -> login.verify(EMAIL, "wrong password " + 1, "1.1.1.1"));
        }

        TooManyAttemptsException blocked = assertThrows(TooManyAttemptsException.class,
                () -> login.verify(EMAIL, PASSWORD, "1.1.1.1"));
        assertEquals(30, blocked.retryAfterSeconds());
        assertTrue(login.verify(EMAIL, PASSWORD, "2.2.2.2").user() != null, "another address is not slowed down");

        clock.addAndGet(31_000_000_000L);
        assertEquals(EMAIL, login.verify(EMAIL, PASSWORD, "1.1.1.1").user().getEmail());
    }
}
