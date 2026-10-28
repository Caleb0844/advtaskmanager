package com.caleb.advtaskmanager.Services;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Enums.Repetition;
import com.caleb.advtaskmanager.Enums.TaskStatus;
import com.caleb.advtaskmanager.Repositories.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskRepetitionService {

    private final TaskRepository taskRepository;
    private final Clock taskClock;
    private final TaskReminderService reminderService;

    public void configureNextOccurrence(Task task) {
        Repetition repetition = task.getRepetition() == null ? Repetition.NONE : task.getRepetition();
        task.setRepetition(repetition);
        if (repetition == Repetition.NONE || task.getSourceTaskId() != null) {
            task.setNextOccurrenceAt(null);
            task.setNextOccurrenceNumber(0);
            return;
        }
        LocalDateTime anchor = anchor(task);
        LocalDateTime now = LocalDateTime.now(taskClock);
        long number = Math.max(1, repetition.periodsBetween(anchor, now));
        while (!repetition.occurrenceAt(anchor, number).isAfter(now)) {
            number++;
        }
        task.setNextOccurrenceNumber(number);
        task.setNextOccurrenceAt(repetition.occurrenceAt(anchor, number));
    }

    @Transactional
    public int generateDueOccurrences(LocalDateTime now) {
        int generated = 0;
        for (Task source : taskRepository.findDueRepeatingTasks(now, PageRequest.of(0, 100))) {
            if (source.getRepetition() == Repetition.NONE) {
                source.setNextOccurrenceAt(null);
                source.setNextOccurrenceNumber(0);
                continue;
            }
            while (!source.getNextOccurrenceAt().isAfter(now) && generated < 100) {
                Task occurrence = occurrence(source);
                reminderService.configure(occurrence);
                source.getUser().addTask(occurrence);
                taskRepository.save(occurrence);
                generated++;
                long next = source.getNextOccurrenceNumber() + 1;
                source.setNextOccurrenceNumber(next);
                source.setNextOccurrenceAt(source.getRepetition().occurrenceAt(anchor(source), next));
            }
            if (generated >= 100) {
                break;
            }
        }
        return generated;
    }

    private Task occurrence(Task source) {
        LocalDateTime next = source.getNextOccurrenceAt();
        LocalDateTime startsAt = source.getStartsAt() == null ? null : next;
        LocalDateTime endsAt = source.getEndsAt() == null ? null
            : next.plus(Duration.between(source.getStartsAt(), source.getEndsAt()));
        LocalDateTime dueDate = source.getStartsAt() == null ? next
            : next.plus(Duration.between(source.getStartsAt(), source.getDueDate()));
        return Task.builder()
            .name(source.getName())
            .description(source.getDescription())
            .priority(source.getPriority())
            .status(TaskStatus.PENDING)
            .user(source.getUser())
            .startsAt(startsAt)
            .endsAt(endsAt)
            .dueDate(dueDate)
            .repetition(Repetition.NONE)
            .reminder(source.getReminder()).reminderAmount(source.getReminderAmount()).reminderUnit(source.getReminderUnit())
            .sourceTaskId(source.getId())
            .build();
    }

    private LocalDateTime anchor(Task task) {
        return task.getStartsAt() == null ? task.getDueDate() : task.getStartsAt();
    }
}
