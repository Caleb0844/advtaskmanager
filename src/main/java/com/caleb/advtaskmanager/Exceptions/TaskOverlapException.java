package com.caleb.advtaskmanager.Exceptions;

import java.util.List;

import com.caleb.advtaskmanager.Dtos.TaskConflict;

public class TaskOverlapException extends RuntimeException {

    private final List<TaskConflict> conflictingTasks;

    public TaskOverlapException(List<TaskConflict> conflictingTasks) {
        super("Another task is scheduled during this time. Do you want to add this task as well?");
        this.conflictingTasks = List.copyOf(conflictingTasks);
    }

    public List<TaskConflict> getConflictingTasks() {
        return conflictingTasks;
    }
}
