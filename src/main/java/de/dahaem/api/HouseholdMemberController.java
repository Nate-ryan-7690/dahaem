package de.dahaem.api;

import de.dahaem.auth.AccountService;
import de.dahaem.auth.HouseholdRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Who belongs to the caller's household, for the household page. */
@RestController
public class HouseholdMemberController {

    private final AccountService accounts;

    public HouseholdMemberController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** One member. {@code email} is given to admins and for the caller's own row; it is null for everyone else. */
    public record MemberDto(int userId, String displayName, String email, String role, boolean you) {
    }

    /**
     * The members of the caller's household, the oldest first: id, display name and role (ADMIN or MEMBER). The email is
     * only shown to an admin and for the caller's own row (a member does not get the others' addresses). {@code you} marks
     * the caller's row.
     */
    @GetMapping("/api/household/members")
    public List<MemberDto> members(Caller caller) {
        boolean admin = caller.role() == HouseholdRole.ADMIN;
        return accounts.members(caller.householdId()).stream().map(m -> {
            boolean you = m.userId() == caller.userId();
            return new MemberDto(m.userId(), m.displayName(), admin || you ? m.email() : null, m.role().name(), you);
        }).toList();
    }
}
