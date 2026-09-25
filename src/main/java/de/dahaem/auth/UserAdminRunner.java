package de.dahaem.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Console;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * Command-line entry point for managing logins. DAHÄM is self-hosted, so there is no public
 * sign-up endpoint: the operator creates the accounts on the server. Run it from the app directory
 * (the one containing data/), against the same database the API uses:
 *
 *   create-user <email> <displayName> [householdName]
 *       The first user creates the household (default name "DAHÄM") and becomes its ADMIN;
 *       later users join that household as MEMBER.
 *   set-password <email>
 *       Sets a new password for an existing user (there are no password-reset emails).
 *
 * The password is read from the DAHAEM_PASSWORD environment variable if it is set (for scripted
 * setups), otherwise prompted for twice on the console without echo. It is never taken as an
 * argument, so it does not end up in shell history or the process list.
 *
 * Exit code: 0 = OK, 1 = failed, 2 = bad arguments.
 */
public class UserAdminRunner {

    static final String PASSWORD_ENV = "DAHAEM_PASSWORD";

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "";
        boolean createUser = command.equals("create-user") && (args.length == 3 || args.length == 4);
        boolean setPassword = command.equals("set-password") && args.length == 2;
        if (!createUser && !setPassword) {
            printUsage();
            System.exit(2);
        }

        AccountService service = new AccountService(new AccountRepository(), new BCryptPasswordEncoder());
        try {
            String password = readPassword();
            if (createUser) {
                CreatedAccount created = service.createUser(args[1], args[2], password,
                        args.length == 4 ? args[3] : null, LocalDate.now());
                Household household = created.membership().household();
                System.out.printf("Created user %s (%s), id %d.%n", created.user().getEmail(),
                        created.user().getDisplayName(), created.user().getId());
                System.out.printf("%s household '%s' (id %d) as %s.%n",
                        created.newHousehold() ? "Created" : "Added to", household.getName(), household.getId(),
                        created.membership().role());
            } else {
                service.setPassword(args[1], password);
                System.out.println("Password updated for " + AccountService.normalizeEmail(args[1]) + ".");
            }
            System.exit(0);
        } catch (RuntimeException e) {
            System.err.println("FAILED: " + e.getMessage());
            if (String.valueOf(e.getCause()).contains("no such table")) {
                System.err.println("The database has no login tables yet. Apply "
                        + "migrations/0002_add_household_and_app_user.sql first (see the file for the command).");
            }
            System.exit(1);
        }
    }

    private static String readPassword() {
        String fromEnv = System.getenv(PASSWORD_ENV);
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return fromEnv;
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("No console to prompt for the password; set " + PASSWORD_ENV + " instead");
        }
        char[] first = console.readPassword("Password (at least %d characters): ", AccountService.MIN_PASSWORD_LENGTH);
        char[] second = console.readPassword("Repeat password: ");
        try {
            if (first == null || second == null || !Arrays.equals(first, second)) {
                throw new IllegalArgumentException("The passwords do not match");
            }
            return new String(first);
        } finally {
            if (first != null) Arrays.fill(first, '\0');
            if (second != null) Arrays.fill(second, '\0');
        }
    }

    private static void printUsage() {
        System.err.println("""
                usage: UserAdminRunner <command>
                  create-user <email> <displayName> [householdName]
                                 create a login; the first user creates the household (as ADMIN),
                                 later users join it (as MEMBER)
                  set-password <email>
                                 set a new password for an existing user
                The password is read from $DAHAEM_PASSWORD, or prompted for on the console.""");
    }
}
