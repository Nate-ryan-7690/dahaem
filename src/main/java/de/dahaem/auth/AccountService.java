package de.dahaem.auth;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Business logic for user accounts and households. No SQL here, no UI here.
 *
 * <p>Passwords only ever pass through here on their way into the {@link PasswordEncoder} (BCrypt in
 * the app); only the resulting hash is handed to the repository. Emails are trimmed and lower-cased
 * before they are stored or looked up, so "Anna@Example.de" and "anna@example.de" are one account.
 *
 * <p>There is deliberately no public sign-up: accounts are created by the operator through
 * {@link UserAdminRunner}. For now there is a single shared household: the first user creates it
 * (as {@link HouseholdRole#ADMIN}), everyone after joins it as {@link HouseholdRole#MEMBER}.
 */
public class AccountService {

    public static final String DEFAULT_HOUSEHOLD_NAME = "DAHÄM";
    public static final int MIN_PASSWORD_LENGTH = 10;
    // BCrypt only looks at the first 72 bytes; Spring's encoder rejects anything longer.
    public static final int MAX_PASSWORD_BYTES = 72;

    // Deliberately loose: something@something, no spaces. The operator types these in by hand.
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Trims and lower-cases an email so lookups and the UNIQUE constraint ignore case. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Creates a login. If no household exists yet, creates one (named householdName, or
     * {@link #DEFAULT_HOUSEHOLD_NAME} when blank) and makes the user its ADMIN; otherwise adds the
     * user to the existing household as a MEMBER. Runs as one transaction: either the user, the
     * household (if new) and the membership are all saved, or none of them.
     *
     * @throws IllegalArgumentException for an invalid email, display name or password
     * @throws IllegalStateException    if a user with this email already exists
     */
    public CreatedAccount createUser(String email, String displayName, String rawPassword,
                                     String householdName, LocalDate today) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || !EMAIL.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("Not a valid email address: " + email);
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name must not be empty");
        }
        validatePassword(rawPassword);
        if (repository.findUserByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException("A user with email " + normalizedEmail + " already exists");
        }
        String passwordHash = passwordEncoder.encode(rawPassword);

        Optional<Household> existing = repository.findFirstHousehold();
        Connection con = repository.openTransaction();
        try {
            Household household = existing.isPresent()
                    ? existing.get()
                    : repository.insertHousehold(con,
                            householdName == null || householdName.isBlank() ? DEFAULT_HOUSEHOLD_NAME : householdName.trim(),
                            today);
            HouseholdRole role = existing.isPresent() ? HouseholdRole.MEMBER : HouseholdRole.ADMIN;
            AppUser user = repository.insertUser(con, normalizedEmail, displayName.trim(), passwordHash, today);
            int memberId = repository.insertMember(con, household.getId(), user.getId(), role);
            con.commit();
            return new CreatedAccount(user, new HouseholdMembership(memberId, household, user.getId(), role),
                    existing.isEmpty());
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Could not create user " + normalizedEmail, e);
        } catch (RuntimeException e) {
            rollback(con);
            throw e;
        } finally {
            close(con);
        }
    }

    /**
     * Sets a new password for an existing user (the operator's stand-in for "forgot password").
     *
     * @throws IllegalArgumentException for an invalid password or an unknown email
     */
    public void setPassword(String email, String rawPassword) {
        validatePassword(rawPassword);
        AppUser user = findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No user with email " + normalizeEmail(email)));
        repository.updatePasswordHash(user.getId(), passwordEncoder.encode(rawPassword));
    }

    public Optional<AppUser> findByEmail(String email) {
        String normalized = normalizeEmail(email);
        return normalized == null ? Optional.empty() : repository.findUserByEmail(normalized);
    }

    public Optional<HouseholdMembership> membershipFor(int userId) {
        return repository.findMembershipForUser(userId);
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    private static void rollback(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void close(Connection con) {
        try {
            con.close();
        } catch (SQLException ignored) {
        }
    }
}
