package com.caleb.advtaskmanager.Enums;

public enum PostponeOption {
    TOMORROW("Tomorrow", 0), MINUTES_5("5 minutes", 5), MINUTES_10("10 minutes", 10),
    MINUTES_30("30 minutes", 30), HOURS_1("1 hour", 60), HOURS_2("2 hours", 120),
    HOURS_3("3 hours", 180), HOURS_6("6 hours", 360), HOURS_12("12 hours", 720),
    DAYS_1("1 day", 1440);

    public final String label;
    public final long minutes;

    PostponeOption(String label, long minutes) {
        this.label = label;
        this.minutes = minutes;
    }
}
