package de.dahaem.auth;

import de.dahaem.shared.Database;
import de.dahaem.testsupport.TestClock;
import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invitations into a household on a throwaway MariaDB: made by an admin, used once, never stored as a code. */
class InvitationServiceTest {

    private static final String PASSWORD = "a long enough password";

    private final AtomicLong nanos = new AtomicLong();
    private final TestClock clock = new TestClock(LocalDate.of(2026, 11, 11));
    private AccountService accounts;
    private InvitationService invitations;
    private AppUser admin;
    private int household;

    @BeforeEach
    void setUp() throws IOException {
        TestDatabase.reset();
        AccountRepository accountRepository = new AccountRepository();
        accounts = new AccountService(accountRepository, new BCryptPasswordEncoder(4));
        CreatedAccount first = accounts.createUser("nate@example.de", "Nate", PASSWORD, "Zuhause", LocalDate.of(2026, 9, 26));
        admin = first.user();
        household = first.membership().household().getId();
        invitations = new InvitationService(new InvitationRepository(), accountRepository, accounts,
                new LoginThrottle(nanos::get), clock);
    }

    @AfterEach
    void tearDown() throws IOException {
        TestDatabase.close();
    }

    @Test
    void aCodeMakesAMemberOfTheInvitingHouseholdAndWorksOnlyOnce() throws SQLException {
        InvitationService.Created created = invitations.create(household, admin.getId(), "Anna");

        assertTrue(created.code().matches("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){3}"), created.code());
        CreatedAccount account = invitations.accept(created.code(), "Anna@Example.de", "Anna", PASSWORD, "1.1.1.1");

        assertEquals("anna@example.de", account.user().getEmail());
        assertEquals(household, account.membership().household().getId());
        assertEquals(HouseholdRole.MEMBER, account.membership().role());
        assertTrue(accounts.verifyPassword("anna@example.de", PASSWORD).isPresent(), "she can log in");
        assertThrows(InvalidInvitationException.class,
                () -> invitations.accept(created.code(), "other@example.de", "Other", PASSWORD, "1.1.1.1"));
        assertEquals(0, count("SELECT COUNT(*) FROM app_user WHERE user_email = 'other@example.de'"));
        InvitationService.Listed listed = invitations.list(household).get(0);
        assertEquals("USED", listed.status());
        assertEquals("anna@example.de", listed.usedByEmail());
    }

    @Test
    void theCodeIsNeverStoredOnlyItsHashAndTheLastFourCharacters() throws SQLException {
        InvitationService.Created created = invitations.create(household, admin.getId(), null);
        String raw = created.code().replace("-", "");

        String hash = text("SELECT household_invite_code_hash FROM household_invite");
        assertEquals(64, hash.length());
        assertFalse(hash.contains(raw));
        assertEquals(raw.substring(12), text("SELECT household_invite_code_hint FROM household_invite"));
        assertEquals(raw.substring(12), invitations.list(household).get(0).hint());
    }

    @Test
    void aCodeIsFoundWhateverTheCaseAndTheDashes() {
        InvitationService.Created created = invitations.create(household, admin.getId(), null);
        String sloppy = created.code().toLowerCase().replace("-", " ");

        CreatedAccount account = invitations.accept(sloppy, "anna@example.de", "Anna", PASSWORD, "1.1.1.1");

        assertEquals("anna@example.de", account.user().getEmail());
    }

    @Test
    void aWrongCodeIsRefusedAndFiveWrongOnesSlowTheAddressDown() {
        invitations.create(household, admin.getId(), null);

        for (int i = 0; i < LoginThrottle.THRESHOLD; i++) {
            assertThrows(InvalidInvitationException.class,
                    () -> invitations.accept("AAAA-AAAA-AAAA-AAAA", "x@example.de", "X", PASSWORD, "9.9.9.9"));
        }
        assertThrows(TooManyAttemptsException.class,
                () -> invitations.accept("AAAA-AAAA-AAAA-AAAA", "x@example.de", "X", PASSWORD, "9.9.9.9"));
        // a code that cannot be one (wrong length, wrong characters) is the same answer
        assertThrows(InvalidInvitationException.class,
                () -> invitations.accept("short", "x@example.de", "X", PASSWORD, "8.8.8.8"));
        assertThrows(InvalidInvitationException.class,
                () -> invitations.accept(null, "x@example.de", "X", PASSWORD, "8.8.8.8"));
    }

    @Test
    void aRightCodeGivesItsAttemptBack() {
        for (int i = 0; i < 20; i++) {
            InvitationService.Created created = invitations.create(household, admin.getId(), null);
            invitations.accept(created.code(), "user" + i + "@example.de", "U" + i, PASSWORD, "5.5.5.5");
        }
        // twenty right codes from one address did not slow it down
        InvitationService.Created again = invitations.create(household, admin.getId(), null);
        invitations.accept(again.code(), "last@example.de", "Last", PASSWORD, "5.5.5.5");
    }

    @Test
    void badDataOrATakenEmailLeavesTheCodeUsable() {
        InvitationService.Created created = invitations.create(household, admin.getId(), null);

        assertThrows(IllegalArgumentException.class,
                () -> invitations.accept(created.code(), "not-an-email", "Anna", PASSWORD, "1.1.1.1"));
        assertThrows(IllegalArgumentException.class,
                () -> invitations.accept(created.code(), "anna@example.de", "Anna", "short", "1.1.1.1"));
        assertThrows(IllegalArgumentException.class,
                () -> invitations.accept(created.code(), "anna@example.de", " ", PASSWORD, "1.1.1.1"));
        assertThrows(EmailTakenException.class,
                () -> invitations.accept(created.code(), "NATE@example.de", "Another Nate", PASSWORD, "1.1.1.1"));

        assertEquals("OPEN", invitations.list(household).get(0).status());
        invitations.accept(created.code(), "anna@example.de", "Anna", PASSWORD, "1.1.1.1");
    }

    @Test
    void anInvitationExpiresAfterSevenDays() {
        InvitationService.Created created = invitations.create(household, admin.getId(), null);
        clock.setToday(LocalDate.of(2026, 11, 18).plusDays(1));   // eight days later

        assertEquals("EXPIRED", invitations.list(household).get(0).status());
        assertThrows(InvalidInvitationException.class,
                () -> invitations.accept(created.code(), "anna@example.de", "Anna", PASSWORD, "1.1.1.1"));
    }

    @Test
    void anInvitationStillWorksOnTheSixthDay() {
        InvitationService.Created created = invitations.create(household, admin.getId(), null);
        clock.setToday(LocalDate.of(2026, 11, 17));   // six days later

        invitations.accept(created.code(), "anna@example.de", "Anna", PASSWORD, "1.1.1.1");
    }

    @Test
    void aRevokedInvitationCannotBeUsedAndOnlyItsOwnHouseholdCanRevokeIt() {
        InvitationService.Created created = invitations.create(household, admin.getId(), null);
        assertThrows(NoSuchElementException.class, () -> invitations.revoke(household + 1, created.id()));
        invitations.revoke(household, created.id());

        assertEquals("REVOKED", invitations.list(household).get(0).status());
        assertThrows(InvalidInvitationException.class,
                () -> invitations.accept(created.code(), "anna@example.de", "Anna", PASSWORD, "1.1.1.1"));
        assertThrows(NoSuchElementException.class, () -> invitations.revoke(household, created.id()),
                "an invitation that is already revoked cannot be revoked again");
    }

    @Test
    void atMostTwentyInvitationsCanBeOpenAtOnce() {
        for (int i = 0; i < InvitationService.MAX_OPEN; i++) {
            invitations.create(household, admin.getId(), null);
        }
        assertThrows(IllegalArgumentException.class, () -> invitations.create(household, admin.getId(), null));

        invitations.revoke(household, invitations.list(household).get(0).id());
        invitations.create(household, admin.getId(), null);   // one place free again
    }

    @Test
    void aTooLongNoteIsRefusedAndTwoCodesDiffer() {
        assertThrows(IllegalArgumentException.class,
                () -> invitations.create(household, admin.getId(), "x".repeat(InvitationService.MAX_NOTE + 1)));

        List<String> codes = List.of(invitations.create(household, admin.getId(), "a").code(),
                invitations.create(household, admin.getId(), "b").code());
        assertNotEquals(codes.get(0), codes.get(1));
        assertNull(invitations.list(household).get(0).usedByEmail());
    }

    // ---------- helpers ----------

    private static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String text(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
