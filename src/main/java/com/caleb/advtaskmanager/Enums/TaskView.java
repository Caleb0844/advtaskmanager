package com.caleb.advtaskmanager.Enums;

public enum TaskView {
    ALL, PENDING, POSTPONED, COMPLETED;

    public TaskStatus taskStatus() {
        return this == ALL ? null : TaskStatus.valueOf(name());
    }
}
