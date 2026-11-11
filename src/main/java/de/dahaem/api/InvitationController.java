package de.dahaem.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.dahaem.auth.CreatedAccount;
import de.dahaem.auth.HouseholdRole;
import de.dahaem.auth.InvitationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Invitations into a household ("Konto erstellen"). An admin of the household makes an invitation and gets its code ONCE;
 * whoever enters the code on the sign-up page gets an account as a MEMBER of that household. There is no open sign-up.
 * The PHP front end shows the pages; every call needs the internal token. The admin calls also need {@code X-Acting-User}.
 */
@RestController
public class InvitationController {

    private final InvitationService invitations;
    private final InternalTokenFilter internalToken;

    public InvitationController(InvitationService invitations, InternalTokenFilter internalToken) {
        this.invitations = invitations;
        this.internalToken = internalToken;
    }

    /** What the admin gets back when making an invitation. {@code code} is shown only here, never again. */
    public record CreatedInvitationDto(int id, String code, String note, Instant expiresAt) {
    }

    /** One invitation in the list: never the code, only its last four characters ({@code hint}). status: OPEN, USED, REVOKED, EXPIRED. */
    public record InvitationDto(int id, String hint, String note, String status, Instant createdAt, Instant expiresAt,
                                String usedByEmail) {
    }

    /** Body of POST /api/invitations/accept. */
    public record AcceptRequest(String code, String email, String displayName, String password) {
    }

    /**
     * Makes an invitation into the caller's household (admins only; 403 {@code NOT_ADMIN} otherwise). Optional body
     * {@code {"note": "Anna"}} (at most 100 characters) to tell invitations apart. 201 with the code, shown only now; it is
     * valid for 7 days and works once. At most 20 invitations can be open at a time (400).
     */
    @PostMapping("/api/household/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedInvitationDto create(@RequestBody(required = false) JsonNode body, Caller caller) {
        requireAdmin(caller);
        String note = null;
        if (body != null && body.hasNonNull("note")) {
            if (!body.get("note").isTextual()) {
                throw new BadRequestException("'note' must be text");
            }
            note = body.get("note").asText();
        }
        InvitationService.Created created = invitations.create(caller.householdId(), caller.userId(), note);
        return new CreatedInvitationDto(created.id(), created.code(), created.note(),
                created.expiresAt().toInstant(ZoneOffset.UTC));
    }

    /** The newest invitations of the caller's household (admins only), newest first, at most 50. */
    @GetMapping("/api/household/invitations")
    public List<InvitationDto> list(Caller caller) {
        requireAdmin(caller);
        return invitations.list(caller.householdId()).stream().map(i -> new InvitationDto(i.id(), i.hint(), i.note(),
                i.status(), i.createdAt().toInstant(ZoneOffset.UTC), i.expiresAt().toInstant(ZoneOffset.UTC),
                i.usedByEmail())).toList();
    }

    /** Revokes an invitation that can still be used (admins only). 204; 404 if there is no such open invitation in the household. */
    @DeleteMapping("/api/household/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable("id") int id, Caller caller) {
        requireAdmin(caller);
        try {
            invitations.revoke(caller.householdId(), id);
        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    /**
     * Uses a code to make an account ("Konto erstellen"): body {@code code} (typed as shown, dashes and case do not matter),
     * {@code email}, {@code displayName}, {@code password} (at least 10 characters). 201 with the new user and the household
     * ({@link CurrentUserDto}, role MEMBER); the PHP then logs the user in. No acting user is needed (nobody is logged in
     * yet). 400 {@code INVALID_INVITATION} for a code that is wrong, used, revoked or expired (one answer for all);
     * 400 {@code BAD_REQUEST} for an invalid email, name or password (the code stays usable); 409 {@code EMAIL_TAKEN}
     * (the code stays usable); 429 {@code TOO_MANY_ATTEMPTS} with {@code Retry-After} after repeated wrong codes from one
     * client address ({@code X-Client-Ip}), like a login.
     */
    @PostMapping("/api/invitations/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentUserDto accept(@RequestBody AcceptRequest request, HttpServletRequest http) {
        if (request.code() == null || request.email() == null || request.displayName() == null
                || request.password() == null) {
            throw new BadRequestException("'code', 'email', 'displayName' and 'password' are required");
        }
        String ip = AuditFilter.ipOf(http, internalToken.hasValidToken(http));
        CreatedAccount created = invitations.accept(request.code(), request.email(), request.displayName(),
                request.password(), ip);
        return CurrentUserDto.from(created.user(), created.membership());
    }

    private static void requireAdmin(Caller caller) {
        if (caller.role() != HouseholdRole.ADMIN) {
            throw new NotAdminException();
        }
    }
}
