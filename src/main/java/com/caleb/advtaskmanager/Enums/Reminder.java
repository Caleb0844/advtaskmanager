package com.caleb.advtaskmanager.Enums;

public enum Reminder {
    NONE("None", 0), MINUTES_5("5 minutes", 5), MINUTES_10("10 minutes", 10),
    MINUTES_30("30 minutes", 30), HOURS_1("1 hour", 60), HOURS_3("3 hours", 180),
    CUSTOM("Custom", 0);

    public final String label;
    public final long minutes;

    Reminder(String label, long minutes) {
        this.label = label;
        this.minutes = minutes;
    }
}
