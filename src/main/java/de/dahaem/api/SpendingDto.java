package de.dahaem.api;

import java.time.LocalDate;

/** Total spent; {@code from}/{@code to} are null when the total covers all purchases. */
public record SpendingDto(LocalDate from, LocalDate to, double total) {
}
