package de.dahaem.auth;

import de.dahaem.shared.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the auth slice against a real, temporary SQLite file built from the project's actual
 * schema.sql. Nothing is mocked: only Database's target file is swapped out, and passwords go
 * through the real BCrypt encoder the app uses.
 */
class AccountServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final String PASSWORD = "correct horse battery";

    private Path dbFile;
    private AccountRepository repository;
    private BCryptPasswordEncoder encoder;
    private AccountService service;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        dbFile = Files.createTempFile("dahaem-auth-test", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty("dahaem.db.path", dbFile.toAbsolutePath().toString());
        createSchemaFromFile();
        repository = new AccountRepository();
        encoder = new BCryptPasswordEncoder();
        service = new AccountService(repository, encoder);
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("dahaem.db.path");
        Files.deleteIfExists(dbFile);
    }

    // ---- Repository ----

    @Test
    void repositoryCreatesHouseholdUserAndMembership() throws SQLException {
        try (Connection con = repository.openTransaction()) {
            Household household = repository.insertHousehold(con, "Zuhause", TODAY);
            AppUser user = repository.insertUser(con, "anna@example.de", "Anna", "hash", TODAY);
            repository.insertMember(con, household.getId(), user.getId(), HouseholdRole.ADMIN);
            con.commit();
        }

        AppUser loaded = repository.findUserByEmail("anna@example.de").orElseThrow();
        assertEquals("Anna", loaded.getDisplayName());
        assertEquals("hash", loaded.getPasswordHash());
        assertEquals(TODAY, loaded.getCreatedDate());

        HouseholdMembership membership = repository.findMembershipForUser(loaded.getId()).orElseThrow();
        assertEquals("Zuhause", membership.household().getName());
        assertEquals(TODAY, membership.household().getCreatedDate());
        assertEquals(HouseholdRole.ADMIN, membership.role());
        assertEquals(membership.household().getId(), repository.findFirstHousehold().orElseThrow().getId());
    }

    @Test
    void repositoryEnforcesUniqueEmail() throws SQLException {
        try (Connection con = repository.openTransaction()) {
            repository.insertUser(con, "anna@example.de", "Anna", "hash", TODAY);
            SQLException e = assertThrows(SQLException.class,
                    () -> repository.insertUser(con, "anna@example.de", "Other Anna", "hash2", TODAY));
            assertTrue(e.getMessage().contains("SQLITE_CONSTRAINT"), e.getMessage());
        }
    }

    @Test
    void repositoryEnforcesOneMembershipPerUserAndHousehold() throws SQLException {
        try (Connection con = repository.openTransaction()) {
            Household household = repository.insertHousehold(con, "Zuhause", TODAY);
            AppUser user = repository.insertUser(con, "anna@example.de", "Anna", "hash", TODAY);
            repository.insertMember(con, household.getId(), user.getId(), HouseholdRole.ADMIN);
            SQLException e = assertThrows(SQLException.class,
                    () -> repository.insertMember(con, household.getId(), user.getId(), HouseholdRole.MEMBER));
            assertTrue(e.getMessage().contains("SQLITE_CONSTRAINT"), e.getMessage());
        }
    }

    @Test
    void repositoryRejectsMembershipForUnknownUser() throws SQLException {
        try (Connection con = repository.openTransaction()) {
            Household household = repository.insertHousehold(con, "Zuhause", TODAY);
            assertThrows(SQLException.class,
                    () -> repository.insertMember(con, household.getId(), 999, HouseholdRole.MEMBER));
        }
    }

    // ---- Password hashing ----

    @Test
    void passwordIsStoredAsBcryptHashThatRoundTripsAndRejectsWrongPasswords() {
        service.createUser("anna@example.de", "Anna", PASSWORD, null, TODAY);

        String hash = repository.findUserByEmail("anna@example.de").orElseThrow().getPasswordHash();
        assertNotEquals(PASSWORD, hash);
        assertFalse(hash.contains(PASSWORD));
        assertTrue(hash.startsWith("$2"), "expected a BCrypt hash, got " + hash);
        assertTrue(encoder.matches(PASSWORD, hash));
        assertFalse(encoder.matches("correct horse battery!", hash));
        assertFalse(encoder.matches("", hash));
    }

    @Test
    void setPasswordReplacesTheHash() {
        service.createUser("anna@example.de", "Anna", PASSWORD, null, TODAY);
        service.setPassword("ANNA@example.de", "a brand new password");

        String hash = repository.findUserByEmail("anna@example.de").orElseThrow().getPasswordHash();
        assertTrue(encoder.matches("a brand new password", hash));
        assertFalse(encoder.matches(PASSWORD, hash));
        assertThrows(IllegalArgumentException.class, () -> service.setPassword("nobody@example.de", PASSWORD));
    }

    // ---- First-user bootstrap ----

    @Test
    void firstUserCreatesTheHouseholdAsAdminAndLaterUsersJoinIt() {
        CreatedAccount first = service.createUser("  Anna@Example.DE ", "Anna", PASSWORD, "Familie M", TODAY);
        CreatedAccount second = service.createUser("ben@example.de", "Ben", PASSWORD, "ignored", TODAY);

        assertTrue(first.newHousehold());
        assertEquals("anna@example.de", first.user().getEmail());
        assertEquals("Familie M", first.membership().household().getName());
        assertEquals(HouseholdRole.ADMIN, first.membership().role());

        assertFalse(second.newHousehold());
        assertEquals(first.membership().household().getId(), second.membership().household().getId());
        assertEquals(HouseholdRole.MEMBER, second.membership().role());
        assertEquals(HouseholdRole.MEMBER,
                service.membershipFor(second.user().getId()).orElseThrow().role());
    }

    @Test
    void householdGetsTheDefaultNameWhenNoneIsGiven() {
        CreatedAccount created = service.createUser("anna@example.de", "Anna", PASSWORD, " ", TODAY);
        assertEquals(AccountService.DEFAULT_HOUSEHOLD_NAME, created.membership().household().getName());
    }

    @Test
    void emailIsUniqueRegardlessOfCase() {
        service.createUser("anna@example.de", "Anna", PASSWORD, null, TODAY);
        assertThrows(IllegalStateException.class,
                () -> service.createUser("ANNA@example.de", "Anna 2", PASSWORD, null, TODAY));
        assertTrue(service.findByEmail(" ANNA@EXAMPLE.DE").isPresent());
    }

    @Test
    void invalidInputIsRejectedAndNothingIsSaved() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("not-an-email", "Anna", PASSWORD, null, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("anna@example.de", " ", PASSWORD, null, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("anna@example.de", "Anna", "short", null, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("anna@example.de", "Anna", "x".repeat(73), null, TODAY));

        assertTrue(repository.findUserByEmail("anna@example.de").isEmpty());
        assertTrue(repository.findFirstHousehold().isEmpty());
    }

    private void createSchemaFromFile() throws IOException, SQLException {
        String schema = Files.readString(Path.of("schema.sql"));
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            for (String rawStatement : schema.split(";")) {
                String statement = rawStatement.replaceAll("--.*", "").trim();
                if (!statement.isEmpty()) {
                    st.execute(statement);
                }
            }
        }
    }
}
