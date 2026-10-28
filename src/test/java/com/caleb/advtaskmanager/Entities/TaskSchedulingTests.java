package com.caleb.advtaskmanager.Entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.caleb.advtaskmanager.SchedulingTestConfiguration;
import com.caleb.advtaskmanager.Enums.Priority;
import com.caleb.advtaskmanager.Enums.Repetition;
import com.caleb.advtaskmanager.Enums.TaskStatus;
import com.caleb.advtaskmanager.Exceptions.TaskOverlapException;
import com.caleb.advtaskmanager.Repositories.TaskRepository;
import com.caleb.advtaskmanager.Repositories.UserRepository;
import com.caleb.advtaskmanager.Services.TaskRepetitionService;
import com.caleb.advtaskmanager.Services.TaskService;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(SchedulingTestConfiguration.class)
class TaskSchedulingTests {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepetitionService repetitionService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void overlappingTaskIsNotSavedUntilConfirmed() {
        User user = user("overlap");
        Task existing = taskService.createTask(request(user, at(23, 12, 0), at(23, 14, 0), Repetition.NONE));
        Task proposed = request(user, at(23, 13, 0), null, Repetition.NONE);

        TaskOverlapException error = assertThrows(TaskOverlapException.class,
            () -> taskService.createTask(proposed));

        assertEquals(existing.getId(), error.getConflictingTasks().getFirst().taskId());
        assertEquals(1, taskRepository.count());
        assertNull(proposed.getId());
        assertEquals(at(23, 12, 0), existing.getStartsAt());
        assertEquals(at(23, 14, 0), existing.getEndsAt());
        taskService.createTask(proposed, true);
        assertEquals(2, taskRepository.count());
        assertEquals(at(23, 12, 0), taskRepository.findById(existing.getId()).orElseThrow().getStartsAt());
        assertEquals(at(23, 14, 0), existing.getEndsAt());
    }

    @Test
    void existingTasksWithoutEndTimeNeverTriggerConfirmation() {
        User user = user("noend");
        taskService.createTask(request(user, at(23, 12, 0), null, Repetition.DAILY));

        taskService.createTask(request(user, at(23, 12, 0), null, Repetition.NONE));
        taskService.createTask(request(user, at(23, 12, 5), null, Repetition.NONE));
        taskService.createTask(request(user, at(23, 11, 55), at(23, 12, 10), Repetition.NONE));
        taskService.createTask(request(user, at(24, 12, 0), at(24, 12, 10), Repetition.NONE));

        assertEquals(5, taskRepository.count());
    }

    @Test
    void adjacentWindowsAndOtherUsersDoNotConflict() {
        User first = user("first");
        User second = user("second");
        taskService.createTask(request(first, at(23, 9, 0), at(23, 10, 0), Repetition.NONE));

        taskService.createTask(request(first, at(23, 10, 0), at(23, 11, 0), Repetition.NONE));
        taskService.createTask(request(second, at(23, 9, 0), at(23, 10, 0), Repetition.NONE));

        assertEquals(3, taskRepository.count());
    }

    @Test
    void optionalEndTimeChecksOnlyTheStartInstant() {
        User user = user("point");
        taskService.createTask(request(user, at(23, 9, 0), at(23, 10, 0), Repetition.NONE));

        assertThrows(TaskOverlapException.class,
            () -> taskService.createTask(request(user, at(23, 9, 30), null, Repetition.NONE)));
        taskService.createTask(request(user, at(23, 10, 0), null, Repetition.NONE));
        assertEquals(2, taskRepository.count());
    }

    @Test
    void confirmationCannotBypassInvalidEndTimes() {
        User user = user("invalid");
        for (LocalDateTime start : new LocalDateTime[] {null, at(23, 10, 0), at(23, 11, 0)}) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> taskService.createTask(request(user, start, at(23, 10, 0), Repetition.NONE), true));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        assertEquals(0, taskRepository.count());
    }

    @Test
    void completedOccurrencesDoNotBlockTime() {
        User user = user("inactive");
        for (TaskStatus status : List.of(TaskStatus.COMPLETED)) {
            Task request = request(user, at(23, 9, 0), at(23, 10, 0), Repetition.NONE);
            request.setStatus(status);
            taskService.createTask(request);
        }

        taskService.createTask(request(user, at(23, 9, 0), at(23, 10, 0), Repetition.NONE));
        assertEquals(2, taskRepository.count());
    }

    @Test
    void reschedulingRequiresConfirmationAndKeepsRejectedChangeUnsaved() {
        User user = user("update");
        taskService.createTask(request(user, at(23, 9, 0), at(23, 10, 0), Repetition.NONE));
        Task existing = taskService.createTask(request(user, at(23, 10, 0), at(23, 11, 0), Repetition.NONE));
        Task update = request(user, at(23, 9, 30), at(23, 10, 30), Repetition.NONE);

        assertThrows(TaskOverlapException.class, () -> taskService.updateTask(existing.getId(), update));
        assertEquals(at(23, 10, 0), existing.getStartsAt());
        taskService.updateTask(existing.getId(), update, true);
        assertEquals(at(23, 9, 30), existing.getStartsAt());
        assertEquals(2, taskRepository.count());
    }

    @Test
    void statusChangeDoesNotAskAgainAboutPreviouslyConfirmedOverlap() {
        User user = user("status");
        Task existing = taskService.createTask(request(user, at(23, 9, 0), at(23, 10, 0), Repetition.NONE));
        taskService.createTask(request(user, at(23, 9, 30), at(23, 10, 30), Repetition.NONE), true);
        Task update = request(user, existing.getStartsAt(), existing.getEndsAt(), Repetition.NONE);
        update.setStatus(TaskStatus.COMPLETED);

        assertEquals(TaskStatus.COMPLETED, taskService.updateTask(existing.getId(), update).getStatus());
    }

    @Test
    void warningIncludesFutureOccurrenceOfExistingRepeatingTask() {
        User user = user("future");
        Task source = taskService.createTask(request(user, at(23, 9, 0), at(23, 10, 0), Repetition.DAILY));

        TaskOverlapException error = assertThrows(TaskOverlapException.class,
            () -> taskService.createTask(request(user, at(25, 9, 30), at(25, 10, 30), Repetition.NONE)));

        assertEquals(source.getId(), error.getConflictingTasks().getFirst().taskId());
        assertEquals(at(25, 9, 0), error.getConflictingTasks().getFirst().startsAt());
    }

    @Test
    void newRepeatingTaskChecksLaterOccurrencesAgainstOneOffTask() {
        User user = user("newseries");
        taskService.createTask(request(user, at(26, 9, 30), at(26, 10, 30), Repetition.NONE));

        assertThrows(TaskOverlapException.class,
            () -> taskService.createTask(request(user, at(23, 9, 0), at(23, 10, 0), Repetition.DAILY)));
        assertEquals(1, taskRepository.count());
    }

    @Test
    void recurringSeriesFindCalendarCollisionAfterBothInitialDates() {
        User user = user("calendar");
        LocalDateTime leapDay = LocalDateTime.of(2028, 2, 29, 9, 0);
        taskService.createTask(request(user, leapDay, leapDay.plusHours(1), Repetition.YEARLY));
        LocalDateTime weeklyStart = LocalDateTime.of(2028, 3, 1, 9, 30);

        TaskOverlapException error = assertThrows(TaskOverlapException.class,
            () -> taskService.createTask(request(user, weeklyStart, weeklyStart.plusHours(1), Repetition.WEEKLY)));

        assertEquals(LocalDateTime.of(2029, 2, 28, 9, 0), error.getConflictingTasks().getFirst().startsAt());
    }

    @Test
    void recurringSeriesAtDifferentTimesCanCoexist() {
        User user = user("nonconflicting");
        taskService.createTask(request(user, at(23, 9, 0), at(23, 10, 0), Repetition.DAILY));
        taskService.createTask(request(user, at(26, 11, 0), at(26, 12, 0), Repetition.WEEKLY));

        assertEquals(2, taskRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = Repetition.class, names = {"DAILY", "WEEKLY", "YEARLY"})
    void generatesDueOccurrenceOnceAndPreservesOwnerAndDuration(Repetition repetition) {
        User user = user("generated");
        LocalDateTime start = at(23, 23, 0);
        Task source = taskService.createTask(request(user, start, start.plusHours(2), repetition));
        LocalDateTime next = repetition.occurrenceAt(start, 1);

        assertEquals(0, repetitionService.generateDueOccurrences(next.minusNanos(1)));
        assertEquals(1, repetitionService.generateDueOccurrences(next));
        assertEquals(0, repetitionService.generateDueOccurrences(next));
        Task generated = occurrences(source).getFirst();
        assertEquals(user.getId(), generated.getUser().getId());
        assertEquals(next, generated.getStartsAt());
        assertEquals(next.plusHours(2), generated.getEndsAt());
        assertEquals(next.plusHours(4), generated.getDueDate());
        assertEquals(TaskStatus.PENDING, generated.getStatus());
        assertEquals(Repetition.NONE, generated.getRepetition());
        assertEquals(repetition.occurrenceAt(start, 2), source.getNextOccurrenceAt());
    }

    @Test
    void monthlyOccurrencesReturnToOriginalDayAfterShortMonth() {
        LocalDateTime january = LocalDateTime.of(2027, 1, 31, 9, 0);
        Task source = taskService.createTask(request(user("monthly"), january, january.plusHours(1), Repetition.MONTHLY));

        assertEquals(1, repetitionService.generateDueOccurrences(LocalDateTime.of(2027, 2, 28, 9, 0)));
        assertEquals(LocalDateTime.of(2027, 3, 31, 9, 0), source.getNextOccurrenceAt());
        assertEquals(1, repetitionService.generateDueOccurrences(LocalDateTime.of(2027, 3, 31, 9, 0)));
        assertEquals(List.of(LocalDateTime.of(2027, 2, 28, 9, 0), LocalDateTime.of(2027, 3, 31, 9, 0)),
            occurrences(source).stream().map(Task::getStartsAt).toList());
    }

    @Test
    void yearlyOccurrencesReturnToLeapDay() {
        LocalDateTime leapDay = LocalDateTime.of(2028, 2, 29, 9, 0);
        Task source = taskService.createTask(request(user("yearly"), leapDay, leapDay.plusHours(1), Repetition.YEARLY));

        assertEquals(4, repetitionService.generateDueOccurrences(LocalDateTime.of(2032, 2, 29, 9, 0)));
        List<Task> generated = occurrences(source);
        assertEquals(LocalDateTime.of(2029, 2, 28, 9, 0), generated.getFirst().getStartsAt());
        assertEquals(LocalDateTime.of(2032, 2, 29, 9, 0), generated.getLast().getStartsAt());
    }

    @Test
    void deadlineIsUsedForRepetitionWhenNoStartTimeIsProvided() {
        Task request = request(user("deadline"), null, null, Repetition.WEEKLY);
        Task source = taskService.createTask(request);
        LocalDateTime next = request.getDueDate().plusWeeks(1);

        assertEquals(next, source.getNextOccurrenceAt());
        assertEquals(1, repetitionService.generateDueOccurrences(next));
        Task generated = occurrences(source).getFirst();
        assertNull(generated.getStartsAt());
        assertNull(generated.getEndsAt());
        assertEquals(next, generated.getDueDate());
    }

    @Test
    void completedSourceStillRepeatsAndStoppingRepetitionKeepsHistory() {
        User user = user("stop");
        Task request = request(user, at(23, 9, 0), at(23, 10, 0), Repetition.DAILY);
        request.setStatus(TaskStatus.COMPLETED);
        Task source = taskService.createTask(request);
        assertEquals(1, repetitionService.generateDueOccurrences(at(24, 9, 0)));
        Task update = request(user, source.getStartsAt(), source.getEndsAt(), Repetition.NONE);

        taskService.updateTask(source.getId(), update);

        assertNull(source.getNextOccurrenceAt());
        assertEquals(0, repetitionService.generateDueOccurrences(at(30, 9, 0)));
        assertEquals(1, occurrences(source).size());
    }

    @Test
    void missedOccurrencesCatchUpInBoundedBatchesWithoutRepeatingCopies() {
        LocalDateTime start = at(23, 9, 0);
        Task source = taskService.createTask(request(user("catchup"), start, start.plusHours(1), Repetition.DAILY));
        LocalDateTime now = start.plusDays(105);

        assertEquals(100, repetitionService.generateDueOccurrences(now));
        assertEquals(5, repetitionService.generateDueOccurrences(now));
        assertEquals(0, repetitionService.generateDueOccurrences(now));
        assertEquals(105, occurrences(source).size());
        assertEquals(start.plusDays(106), source.getNextOccurrenceAt());
    }

    @Test
    void pastScheduleStartsFutureRepetitionAtNextUpcomingTime() {
        LocalDateTime past = LocalDateTime.of(2026, 10, 1, 9, 0);
        Task source = taskService.createTask(request(user("past"), past, past.plusHours(1), Repetition.DAILY));

        assertEquals(at(22, 9, 0), source.getNextOccurrenceAt());
        assertEquals(0, repetitionService.generateDueOccurrences(at(22, 8, 59)));
        assertEquals(1, repetitionService.generateDueOccurrences(at(22, 9, 0)));
    }

    private User user(String username) {
        return userRepository.saveAndFlush(User.builder().name(username).username(username)
            .email(username + "@example.com").build());
    }

    private Task request(User user, LocalDateTime start, LocalDateTime end, Repetition repetition) {
        return Task.builder().name("Scheduled task").description("Planning")
            .priority(Priority.MEDIUM).status(TaskStatus.PENDING).user(user)
            .dueDate(start == null ? at(23, 12, 0) : start.plusHours(4))
            .startsAt(start).endsAt(end).repetition(repetition).build();
    }

    private LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 10, day, hour, minute);
    }

    private List<Task> occurrences(Task source) {
        return taskRepository.findAll().stream()
            .filter(task -> source.getId().equals(task.getSourceTaskId()))
            .sorted(Comparator.comparing(Task::getDueDate)).toList();
    }
}
