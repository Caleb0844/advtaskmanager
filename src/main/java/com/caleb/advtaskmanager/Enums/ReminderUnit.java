package com.caleb.advtaskmanager.Enums;

import java.time.Duration;

public enum ReminderUnit {
    MINUTES, HOURS, DAYS, WEEKS;

    public Duration duration(long amount) {
        return switch (this) {
            case MINUTES -> Duration.ofMinutes(amount);
            case HOURS -> Duration.ofHours(amount);
            case DAYS -> Duration.ofDays(amount);
            case WEEKS -> Duration.ofDays(Math.multiplyExact(amount, 7));
        };
    }
}
