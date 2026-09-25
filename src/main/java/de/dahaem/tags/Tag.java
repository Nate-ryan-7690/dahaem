package de.dahaem.tags;

/**
 * Plain data holder for one row of the tag table.
 * No logic here on purpose just the fields, a constructor, and getters.
 */

public class Tag {

    private final int id;
    private final String name;
    private final String category;

    public Tag(int id, String name, String category) {
        this.id = id;
        this.name = name;
        this.category = category;
    }

    public int getId(){ return id; }

    public String getName(){ return name; }

    public String getCategory(){ return category; }

    @Override
    public String toString() {
        return "Tag{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                "}";
    }
}
