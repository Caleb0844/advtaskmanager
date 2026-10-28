package com.caleb.advtaskmanager.Services;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "tasks.pending.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class TaskPendingScheduler {
    private final TaskPendingService pendingService;
    private final Clock taskClock;

    @Scheduled(fixedDelayString = "${tasks.pending.check-interval-ms:60000}")
    public void refreshPendingTasks() {
        pendingService.refreshPendingTasks(LocalDateTime.now(taskClock));
    }
}
