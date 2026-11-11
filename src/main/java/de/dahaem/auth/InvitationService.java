package de.dahaem.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.OptionalLong;

/**
 * Invitations into a household: an admin makes one, the code is shown ONCE, and whoever enters it (with an email, a name and
 * a password) becomes a {@link HouseholdRole#MEMBER} of that household. There is no open sign-up: without a valid code nobody
 * can make an account.
 *
 * <p>The code is 16 characters from an alphabet of 32 without look-alikes (80 bits), shown as XXXX-XXXX-XXXX-XXXX. Only its
 * SHA-256 is stored. An invitation works once, for {@value #VALID_DAYS} days, and an admin can revoke it. Entering a code
 * counts as an attempt of the client address ({@link LoginThrottle}, the same slow-down as a login); a right code gives
 * the attempt back. No SQL here, no UI here.
 */
public class InvitationService {

    public static final int VALID_DAYS = 7;
    public static final int MAX_OPEN = 20;
    public static final int MAX_NOTE = 100;
    public static final int LIST_LIMIT = 50;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // no I, O, 0, 1
    private static final int CODE_LENGTH = 16;

    /** A new invitation, with the one chance to see its code. */
    public record Created(int id, String code, String note, LocalDateTime expiresAt) {
    }

    /** What an admin sees in the list: never the code. */
    public record Listed(int id, String hint, String note, String status, LocalDateTime createdAt,
                         LocalDateTime expiresAt, String usedByEmail) {
    }

    private final InvitationRepository invitations;
    private final AccountRepository accountRepository;
    private final AccountService accounts;
    private final LoginThrottle throttle;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public InvitationService(InvitationRepository invitations, AccountRepository accountRepository,
                             AccountService accounts, LoginThrottle throttle, Clock clock) {
        this.invitations = invitations;
        this.accountRepository = accountRepository;
        this.accounts = accounts;
        this.throttle = throttle;
        this.clock = clock;
    }

    /**
     * Makes an invitation into the household and returns its code, which is not stored and cannot be shown again.
     *
     * @throws IllegalArgumentException for a note that is too long, or when the household already has {@value #MAX_OPEN}
     *                                  invitations open (revoke one first)
     */
    public Created create(int householdId, int createdBy, String note) {
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > MAX_NOTE) {
            throw new IllegalArgumentException("'note' must be at most " + MAX_NOTE + " characters");
        }
        LocalDateTime now = now();
        if (invitations.countOpen(householdId, now) >= MAX_OPEN) {
            throw new IllegalArgumentException("There are already " + MAX_OPEN + " open invitations: revoke one first");
        }
        String raw = randomCode();
        LocalDateTime expires = now.plus(Duration.ofDays(VALID_DAYS));
        int id = invitations.insert(householdId, createdBy, hash(raw), raw.substring(CODE_LENGTH - 4), cleanNote, now, expires);
        return new Created(id, format(raw), cleanNote, expires);
    }

    /** The newest invitations of the household (at most {@value #LIST_LIMIT}), with their status. */
    public List<Listed> list(int householdId) {
        LocalDateTime now = now();
        return invitations.list(householdId, LIST_LIMIT).stream().map(i -> new Listed(i.id(), i.hint(), i.note(),
                status(i, now), i.createdAt(), i.expiresAt(), i.usedByEmail())).toList();
    }

    /** @throws NoSuchElementException if the household has no such invitation that can still be used */
    public void revoke(int householdId, int invitationId) {
        if (!invitations.revoke(householdId, invitationId, now())) {
            throw new NoSuchElementException("No open invitation with id " + invitationId);
        }
    }

    /**
     * Uses a code: makes the account and puts it into the household of the invitation as a MEMBER, in one transaction (the
     * invitation is used up in the same step, so two people cannot both use it).
     *
     * @param ip the client address (for the throttle)
     * @throws TooManyAttemptsException   while this address is being slowed down (the code is not even looked at)
     * @throws InvalidInvitationException for a code that is wrong, used, revoked or expired
     * @throws IllegalArgumentException   for an invalid email, name or password (the code stays usable)
     * @throws EmailTakenException        if another account has the email (the code stays usable)
     */
    public CreatedAccount accept(String rawCode, String email, String displayName, String rawPassword, String ip) {
        OptionalLong wait = throttle.begin(ip);   // counted before anything is looked at
        if (wait.isPresent()) {
            throw new TooManyAttemptsException(wait.getAsLong());
        }
        String normalized = normalize(rawCode);
        if (normalized == null) {
            throw new InvalidInvitationException();
        }
        String codeHash = hash(normalized);
        InvitationRepository.Invite found = invitations.findByHash(codeHash)
                .filter(i -> i.isOpen(now())).orElseThrow(InvalidInvitationException::new);
        throttle.succeeded(ip);   // the code was right: only wrong codes count as attempts

        AccountService.NewUser newUser = accounts.prepareNewUser(email, displayName, rawPassword);   // the slow part, no lock held
        Connection con = accountRepository.openTransaction();
        try {
            InvitationRepository.Invite locked = invitations.lockByHash(con, codeHash)
                    .filter(i -> i.isOpen(now())).orElseThrow(InvalidInvitationException::new);
            java.time.LocalDate today = now().toLocalDate();
            AppUser user = accountRepository.insertUser(con, newUser.email(), newUser.displayName(),
                    newUser.passwordHash(), today);
            int memberId = accountRepository.insertMember(con, locked.householdId(), user.getId(), HouseholdRole.MEMBER);
            invitations.markUsed(con, locked.id(), user.getId(), now());
            con.commit();
            Household household = accountRepository.findHousehold(locked.householdId()).orElseThrow();
            return new CreatedAccount(user, new HouseholdMembership(memberId, household, user.getId(), HouseholdRole.MEMBER),
                    false);
        } catch (SQLIntegrityConstraintViolationException e) {
            rollback(con);
            throw new EmailTakenException();   // two people took the same address at once
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Could not use invitation " + found.id(), e);
        } catch (RuntimeException e) {
            rollback(con);
            throw e;
        } finally {
            close(con);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String status(InvitationRepository.Invite invite, LocalDateTime now) {
        if (invite.usedAt() != null) {
            return "USED";
        }
        if (invite.revokedAt() != null) {
            return "REVOKED";
        }
        return invite.expiresAt().isAfter(now) ? "OPEN" : "EXPIRED";
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** XXXX-XXXX-XXXX-XXXX */
    static String format(String raw) {
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    /** The 16 characters of a typed code (spaces and dashes ignored, any case), or null if it cannot be a code. */
    static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String s = typed.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (s.length() != CODE_LENGTH) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (ALPHABET.indexOf(s.charAt(i)) < 0) {
                return null;
            }
        }
        return s;
    }

    private static String hash(String normalizedCode) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizedCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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
