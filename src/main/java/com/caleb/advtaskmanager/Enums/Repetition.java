package com.caleb.advtaskmanager.Enums;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public enum Repetition {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    public LocalDateTime occurrenceAt(LocalDateTime anchor, long number) {
        return switch (this) {
            case NONE -> anchor;
            case DAILY -> anchor.plusDays(number);
            case WEEKLY -> anchor.plusWeeks(number);
            case MONTHLY -> anchor.plusMonths(number);
            case YEARLY -> anchor.plusYears(number);
        };
    }

    public long periodsBetween(LocalDateTime anchor, LocalDateTime time) {
        return switch (this) {
            case NONE -> 0;
            case DAILY -> ChronoUnit.DAYS.between(anchor, time);
            case WEEKLY -> ChronoUnit.WEEKS.between(anchor, time);
            case MONTHLY -> ChronoUnit.MONTHS.between(anchor, time);
            case YEARLY -> ChronoUnit.YEARS.between(anchor, time);
        };
    }
}
