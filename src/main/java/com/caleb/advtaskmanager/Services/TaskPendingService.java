package com.caleb.advtaskmanager.Services;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Enums.TaskStatus;
import com.caleb.advtaskmanager.Repositories.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskPendingService {

    private final TaskRepository taskRepository;

    @Transactional
    public int refreshPendingTasks(LocalDateTime now) {
        var missed = taskRepository.findMissedPostponedTasks(now);
        for (Task task : missed) task.setStatus(TaskStatus.PENDING);
        return missed.size();
    }
}
