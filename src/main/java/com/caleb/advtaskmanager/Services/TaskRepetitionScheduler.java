package com.caleb.advtaskmanager.Services;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "tasks.repetition.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class TaskRepetitionScheduler {

    private final TaskRepetitionService repetitionService;
    private final Clock taskClock;

    @Scheduled(fixedDelayString = "${tasks.repetition.check-interval-ms:60000}")
    public void generateDueTasks() {
        LocalDateTime now = LocalDateTime.now(taskClock);
        repetitionService.generateDueOccurrences(now);
    }
}
