package de.dahaem.auth;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AccountService.verifyPassword on a throwaway MariaDB. */
class AccountVerifyTest {

    private static final String EMAIL = "anna@example.de";
    private static final String PASSWORD = "correct horse battery";

    private AccountService accounts;

    @BeforeEach
    void setUp() throws IOException {
        TestDatabase.reset();
        accounts = new AccountService(new AccountRepository(), new BCryptPasswordEncoder(4));
        accounts.createUser(EMAIL, "Anna", PASSWORD, "Zuhause", LocalDate.of(2026, 9, 25));
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void theRightPasswordIsAcceptedWhateverTheCaseOfTheEmail() {
        assertTrue(accounts.verifyPassword(EMAIL, PASSWORD).isPresent());
        assertTrue(accounts.verifyPassword(" Anna@Example.DE ", PASSWORD).isPresent());
    }

    @Test
    void wrongUnknownAndImpossiblePasswordsAreAllJustEmpty() {
        assertFalse(accounts.verifyPassword(EMAIL, "wrong password").isPresent());
        assertFalse(accounts.verifyPassword("nobody@example.de", PASSWORD).isPresent());
        assertFalse(accounts.verifyPassword(EMAIL, "").isPresent());
        assertFalse(accounts.verifyPassword(EMAIL, null).isPresent());
        assertFalse(accounts.verifyPassword(null, PASSWORD).isPresent());
        assertFalse(accounts.verifyPassword(EMAIL, "x".repeat(73)).isPresent(), "longer than BCrypt looks at");
    }

    @Test
    void aHashMadeWithALowerCostIsMadeAgainWithTheCurrentOneAtTheNextLogin() {
        String before = accounts.findByEmail(EMAIL).orElseThrow().getPasswordHash();
        assertTrue(before.contains("$04$"), before);
        AccountService stronger = new AccountService(new AccountRepository(), new BCryptPasswordEncoder(5));

        assertTrue(stronger.verifyPassword(EMAIL, PASSWORD).isPresent());

        String after = accounts.findByEmail(EMAIL).orElseThrow().getPasswordHash();
        assertTrue(after.contains("$05$"), after);
        assertTrue(accounts.verifyPassword(EMAIL, PASSWORD).isPresent(), "the new hash still checks out");
        assertEquals(after, stronger.findByEmail(EMAIL).orElseThrow().getPasswordHash(), "and is not made again");
    }
}
