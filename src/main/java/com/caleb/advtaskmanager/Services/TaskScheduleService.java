package com.caleb.advtaskmanager.Services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.caleb.advtaskmanager.Dtos.TaskConflict;
import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Enums.Repetition;
import com.caleb.advtaskmanager.Enums.TaskStatus;
import com.caleb.advtaskmanager.Exceptions.TaskOverlapException;
import com.caleb.advtaskmanager.Repositories.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskScheduleService {

    private final TaskRepository taskRepository;

    public void checkForOverlap(Task task, Long userId, Long excludedTaskId, boolean confirmed) {
        if (confirmed || task.getStartsAt() == null) {
            return;
        }
        List<TaskConflict> conflicts = new ArrayList<>();
        List<Pattern> requestedPatterns = patterns(task);
        for (Task existing : taskRepository.findByUser_IdAndStartsAtIsNotNull(userId)) {
            if (existing.getEndsAt() == null || Objects.equals(existing.getId(), excludedTaskId)) {
                continue;
            }
            Window conflict = firstConflict(requestedPatterns, patterns(existing));
            if (conflict != null) {
                conflicts.add(new TaskConflict(existing.getId(), existing.getName(), conflict.start(), conflict.end()));
            }
        }
        if (!conflicts.isEmpty()) {
            throw new TaskOverlapException(conflicts);
        }
    }

    private List<Pattern> patterns(Task task) {
        List<Pattern> patterns = new ArrayList<>();
        if (task.getStartsAt() == null) {
            return patterns;
        }
        Duration duration = task.getEndsAt() == null ? null
            : Duration.between(task.getStartsAt(), task.getEndsAt());
        if (task.getStatus() != TaskStatus.COMPLETED) {
            patterns.add(new Pattern(task.getStartsAt(), duration, Repetition.NONE, 0));
        }
        if (task.getSourceTaskId() == null && task.getRepetition() != null
                && task.getRepetition() != Repetition.NONE && task.getNextOccurrenceAt() != null) {
            patterns.add(new Pattern(task.getStartsAt(), duration, task.getRepetition(),
                Math.max(1, task.getNextOccurrenceNumber())));
        }
        return patterns;
    }

    private Window firstConflict(List<Pattern> requested, List<Pattern> existing) {
        Window first = null;
        for (Pattern left : requested) {
            for (Pattern right : existing) {
                Window match = overlap(left, right);
                if (match != null && (first == null || match.start().isBefore(first.start()))) {
                    first = match;
                }
            }
        }
        return first;
    }

    private Window overlap(Pattern requested, Pattern existing) {
        if (requested.repetition() == Repetition.NONE) {
            Window window = requested.at(0);
            for (Window other : existing.near(window.start())) {
                if (overlaps(window, other)) {
                    return other;
                }
            }
            return null;
        }
        if (existing.repetition() == Repetition.NONE) {
            Window window = existing.at(0);
            return requested.near(window.start()).stream().anyMatch(other -> overlaps(other, window))
                ? window : null;
        }

        // Search the less frequent series. Gregorian calendar patterns repeat every 400 years;
        // schedules involving only days and weeks repeat within one week.
        boolean iterateExisting = existing.repetition().ordinal() >= requested.repetition().ordinal();
        Pattern sparse = iterateExisting ? existing : requested;
        Pattern dense = iterateExisting ? requested : existing;
        LocalDateTime firstStart = requested.at(requested.minimum()).start();
        LocalDateTime otherStart = existing.at(existing.minimum()).start();
        LocalDateTime from = firstStart.isAfter(otherStart) ? firstStart : otherStart;
        boolean onlyDaysAndWeeks = sparse.repetition().ordinal() <= Repetition.WEEKLY.ordinal();
        LocalDateTime until = onlyDaysAndWeeks ? from.plusWeeks(1) : from.plusYears(400);
        for (long number = sparse.indexNear(from); ; number++) {
            Window window = sparse.at(number);
            if (window.start().isAfter(until)) {
                return null;
            }
            for (Window other : dense.near(window.start())) {
                if (overlaps(window, other)) {
                    return iterateExisting ? window : other;
                }
            }
        }
    }

    private boolean overlaps(Window left, Window right) {
        if (left.end() == null && right.end() == null) {
            return left.start().equals(right.start());
        }
        if (left.end() == null) {
            return !left.start().isBefore(right.start()) && left.start().isBefore(right.end());
        }
        if (right.end() == null) {
            return !right.start().isBefore(left.start()) && right.start().isBefore(left.end());
        }
        return left.start().isBefore(right.end()) && right.start().isBefore(left.end());
    }

    private record Window(LocalDateTime start, LocalDateTime end) {
    }

    private record Pattern(LocalDateTime anchor, Duration duration, Repetition repetition, long minimum) {

        Window at(long number) {
            LocalDateTime start = repetition.occurrenceAt(anchor, number);
            return new Window(start, duration == null ? null : start.plus(duration));
        }

        long indexNear(LocalDateTime time) {
            long number = Math.max(minimum, repetition.periodsBetween(anchor, time));
            while (number > minimum && at(number).start().isAfter(time)) {
                number--;
            }
            while (!at(number + 1).start().isAfter(time)) {
                number++;
            }
            return number;
        }

        List<Window> near(LocalDateTime time) {
            if (repetition == Repetition.NONE) {
                return List.of(at(0));
            }
            long number = indexNear(time);
            return List.of(at(number), at(number + 1));
        }
    }
}
