package de.dahaem.auth;

import java.time.LocalDate;

/**
 * Plain data holder for one row of the app_user table.
 * passwordHash is the BCrypt hash, never the password itself; toString() leaves it out
 * so it cannot end up in a log line by accident.
 */
public class AppUser {

    private final int id;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final LocalDate createdDate;

    public AppUser(int id, String email, String displayName, String passwordHash, LocalDate createdDate) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdDate = createdDate;
    }

    public int getId() { return id; }

    public String getEmail() { return email; }

    public String getDisplayName() { return displayName; }

    public String getPasswordHash() { return passwordHash; }

    public LocalDate getCreatedDate() { return createdDate; }

    @Override
    public String toString() {
        return "AppUser{id=" + id + ", email='" + email + "', displayName='" + displayName
                + "', createdDate=" + createdDate + '}';
    }
}
