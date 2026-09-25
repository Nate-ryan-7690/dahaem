package de.dahaem.auth;

import java.time.LocalDate;

/**
 * Plain data holder for one row of the household table.
 * No logic here on purpose just the fields, a constructor, and getters.
 */
public class Household {

    private final int id;
    private final String name;
    private final LocalDate createdDate;

    public Household(int id, String name, LocalDate createdDate) {
        this.id = id;
        this.name = name;
        this.createdDate = createdDate;
    }

    public int getId() { return id; }

    public String getName() { return name; }

    public LocalDate getCreatedDate() { return createdDate; }

    @Override
    public String toString() {
        return "Household{id=" + id + ", name='" + name + "', createdDate=" + createdDate + '}';
    }
}
