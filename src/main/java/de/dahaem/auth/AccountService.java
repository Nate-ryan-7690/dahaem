package de.dahaem.auth;

import de.dahaem.shared.Portions;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
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
    static final int MAX_EMAIL_LENGTH = 254;
    /** BCrypt cost of newly made hashes: 12 is about 4 times the work of the old default 10 (a login takes ~0.25 s). */
    public static final int DEFAULT_BCRYPT_STRENGTH = 12;
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

    private volatile String dummyHash;

    /** A hash nobody knows the password of, to spend the same time on an unknown email as on a wrong password. */
    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-" + UUID.randomUUID());
        }
        return dummyHash;
    }

    /**
     * Checks a password. Empty for a wrong password, an unknown email and a password that could never have been set (empty
     * or longer than BCrypt's 72 bytes), and the time spent is the same for all of them. A right password whose hash was
     * made with a lower BCrypt cost than the current one is hashed again with the current cost.
     */
    public Optional<AppUser> verifyPassword(String email, String rawPassword) {
        Optional<AppUser> user = findByEmail(email);
        boolean possible = rawPassword != null && !rawPassword.isEmpty()
                && rawPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
        if (user.isEmpty() || !possible) {
            passwordEncoder.matches(possible ? rawPassword : "x", dummyHash());
            return Optional.empty();
        }
        AppUser found = user.get();
        if (!passwordEncoder.matches(rawPassword, found.getPasswordHash())) {
            return Optional.empty();
        }
        if (passwordEncoder.upgradeEncoding(found.getPasswordHash())) {
            repository.updatePasswordHash(found.getId(), passwordEncoder.encode(rawPassword));
        }
        return user;
    }

    public Optional<AppUser> findById(int userId) {
        return repository.findUserById(userId);
    }

    /**
     * Sets a new email for a user (the caller has already checked the user's password). The email is trimmed and
     * lower-cased like at sign-up.
     *
     * @throws IllegalArgumentException for an address that is not valid
     * @throws EmailTakenException      if another account has it
     */
    public AppUser changeEmail(int userId, String newEmail) {
        String normalized = normalizeEmail(newEmail);
        if (normalized == null || normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Not a valid email address");
        }
        Optional<AppUser> holder = repository.findUserByEmail(normalized);
        if (holder.isPresent() && holder.get().getId() != userId) {
            throw new EmailTakenException();
        }
        if (!repository.updateEmail(userId, normalized)) {
            throw new NoSuchElementException("No user with id " + userId);
        }
        return repository.findUserById(userId).orElseThrow(() -> new NoSuchElementException("No user with id " + userId));
    }

    /** Sets a new password for a user (the caller has already checked the old one). */
    public void changePassword(int userId, String newPassword) {
        validatePassword(newPassword);
        if (!repository.updatePasswordHash(userId, passwordEncoder.encode(newPassword))) {
            throw new NoSuchElementException("No user with id " + userId);
        }
    }

    public Optional<AppUser> findByEmail(String email) {
        String normalized = normalizeEmail(email);
        return normalized == null ? Optional.empty() : repository.findUserByEmail(normalized);
    }

    public Optional<HouseholdMembership> membershipFor(int userId) {
        return repository.findMembershipForUser(userId);
    }

    /** The shared household of this installation (the oldest one), or empty on a fresh database. */
    public Optional<Household> findFirstHousehold() {
        return repository.findFirstHousehold();
    }

    public Optional<Household> findHousehold(int householdId) {
        return repository.findHousehold(householdId);
    }

    /**
     * Sets how many people the household feeds (household_people). Any account of the household may do
     * this; the caller (PHP) decides who is allowed to call it.
     *
     * @return the household as it is now
     * @throws IllegalArgumentException if people is below 1
     * @throws NoSuchElementException   if there is no such household
     */
    public Household setHouseholdPeople(int householdId, int people) {
        if (!Portions.isValidPeople(people)) {
            throw new IllegalArgumentException(Portions.INVALID_PEOPLE_MESSAGE);
        }
        if (!repository.updateHouseholdPeople(householdId, people)) {
            throw new NoSuchElementException("No household with id " + householdId);
        }
        return repository.findHousehold(householdId)
                .orElseThrow(() -> new NoSuchElementException("No household with id " + householdId));
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
