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
    private final Integer people;

    public Household(int id, String name, LocalDate createdDate) {
        this(id, name, createdDate, null);
    }

    /** people is the household's head count (household_people); null until someone has set it. */
    public Household(int id, String name, LocalDate createdDate, Integer people) {
        this.id = id;
        this.name = name;
        this.createdDate = createdDate;
        this.people = people;
    }

    public int getId() { return id; }

    public String getName() { return name; }

    public LocalDate getCreatedDate() { return createdDate; }

    public Integer getPeople() { return people; }

    @Override
    public String toString() {
        return "Household{id=" + id + ", name='" + name + "', createdDate=" + createdDate
                + ", people=" + people + '}';
    }
}
