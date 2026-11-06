package de.dahaem.mealplanner;

import java.time.LocalDate;

/**
 * Thrown when a planned meal on a day before today would be added, changed, moved or removed. Meals of past days
 * are history (their servings were frozen overnight); today and later stay editable. Mapped to 409 by the API.
 */
public class PastDayException extends RuntimeException {

    public PastDayException(LocalDate day, LocalDate today) {
        super("The day " + day + " is in the past (today is " + today + "): meals of past days cannot be changed");
    }
}
