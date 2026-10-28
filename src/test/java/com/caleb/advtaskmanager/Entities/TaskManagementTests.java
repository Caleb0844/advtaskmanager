package com.caleb.advtaskmanager.Entities;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

import com.caleb.advtaskmanager.SchedulingTestConfiguration;
import com.caleb.advtaskmanager.Enums.*;
import com.caleb.advtaskmanager.Exceptions.TaskOverlapException;
import com.caleb.advtaskmanager.Repositories.*;
import com.caleb.advtaskmanager.Services.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(SchedulingTestConfiguration.class)
class TaskManagementTests {
    @Autowired TaskService service;
    @Autowired TaskPendingService pending;
    @Autowired TaskRepetitionService repetition;
    @Autowired TaskRepository tasks;
    @Autowired UserRepository users;

    @ParameterizedTest
    @EnumSource(value = Reminder.class, mode = EnumSource.Mode.EXCLUDE, names = "CUSTOM")
    void reminderPresetsAreCalculatedFromStartTime(Reminder reminder) {
        Task request = request(user("preset"), at(23, 9));
        request.setReminder(reminder);
        Task task = service.createTask(request);
        assertEquals(reminder == Reminder.NONE ? null : at(23, 9).minusMinutes(reminder.minutes), task.getRemindAt());
    }

    @ParameterizedTest
    @EnumSource(ReminderUnit.class)
    void customReminderUsesSpecifiedUnitAndDeadlineWhenStartIsAbsent(ReminderUnit unit) {
        Task request = request(user("custom"), null);
        request.setReminder(Reminder.CUSTOM);
        request.setReminderAmount(2L);
        request.setReminderUnit(unit);
        Task task = service.createTask(request);
        assertEquals(task.getDueDate().minus(unit.duration(2)), task.getRemindAt());
    }

    @Test
    void invalidCustomReminderDoesNotChangeSavedTask() {
        Task task = service.createTask(request(user("invalid"), at(23, 9)));
        for (long amount : new long[] {0, -1, Long.MAX_VALUE}) {
            assertThrows(ResponseStatusException.class, () -> service.updateTask(task.getId(),
                Map.of("reminder", "CUSTOM", "reminderAmount", amount, "reminderUnit", "WEEKS"), true));
        }
        assertThrows(ResponseStatusException.class, () -> service.updateTask(task.getId(),
            Map.of("reminder", "CUSTOM", "reminderAmount", 5), false));
        assertEquals(Reminder.NONE, task.getReminder());
        assertNull(task.getRemindAt());
    }

    @Test
    void checkboxAndStatusStaySynchronizedWithPartialPut() {
        Task task = service.createTask(request(user("checkbox"), at(23, 9)));
        service.updateTask(task.getId(), Map.of("completed", true), false);
        assertTrue(task.isCompleted());
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        service.updateTask(task.getId(), Map.of("completed", false), false);
        assertFalse(task.isCompleted());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        service.updateTask(task.getId(), Map.of("status", "COMPLETED"), false);
        assertTrue(task.isCompleted());
        assertEquals(at(23, 9), task.getStartsAt());
    }

    @Test
    void listsAreSortedFilteredAndIsolatedByUser() {
        User first = user("first");
        User second = user("second");
        Task later = service.createTask(request(first, at(25, 9)));
        Task earlier = service.createTask(request(first, at(23, 9)));
        Task completed = service.createTask(request(first, at(24, 9)));
        service.updateTask(completed.getId(), Map.of("completed", true), false);
        service.createTask(request(second, at(22, 9)));

        assertEquals(List.of(earlier.getId(), completed.getId(), later.getId()),
            service.getTasks(first.getId(), TaskView.ALL).stream().map(Task::getId).toList());
        assertEquals(List.of(earlier.getId(), later.getId()),
            service.getTasks(first.getId(), TaskView.PENDING).stream().map(Task::getId).toList());
        assertEquals(List.of(completed.getId()),
            service.getTasks(first.getId(), TaskView.COMPLETED).stream().map(Task::getId).toList());
    }

    @Test
    void missedTasksReturnToPendingButCompletedAndFuturePostponedTasksDoNot() {
        User user = user("missed");
        Task missed = request(user, at(21, 9));
        missed.setStatus(TaskStatus.POSTPONED);
        Task saved = service.createTask(missed);
        Task completed = request(user, at(21, 12));
        completed.setStatus(TaskStatus.COMPLETED);
        Task finished = service.createTask(completed);
        Task future = request(user, at(23, 9));
        future.setStatus(TaskStatus.POSTPONED);
        Task waiting = service.createTask(future);

        Task noEnd = request(user, null);
        noEnd.setDueDate(at(21, 12));
        noEnd.setStatus(TaskStatus.POSTPONED);
        Task missedWithoutEnd = service.createTask(noEnd);

        assertEquals(2, pending.refreshPendingTasks(at(22, 3)));
        assertEquals(TaskStatus.PENDING, saved.getStatus());
        assertEquals(TaskStatus.PENDING, missedWithoutEnd.getStatus());
        assertEquals(TaskStatus.COMPLETED, finished.getStatus());
        assertEquals(TaskStatus.POSTPONED, waiting.getStatus());
        assertEquals(0, pending.refreshPendingTasks(at(22, 3)));
    }

    @ParameterizedTest
    @EnumSource(PostponeOption.class)
    void postponementPreservesDurationDeadlineAndReminder(PostponeOption option) {
        Task request = request(user("postpone"), at(23, 9));
        request.setReminder(Reminder.MINUTES_5);
        Task task = service.createTask(request);
        LocalDateTime expected = option == PostponeOption.TOMORROW ? at(23, 9) : at(22, 3).plusMinutes(option.minutes);

        service.postponeTask(task.getId(), option, false);

        assertEquals(expected, task.getStartsAt());
        assertEquals(expected.plusHours(1), task.getEndsAt());
        assertEquals(expected.plusHours(2), task.getDueDate());
        assertEquals(expected.minusMinutes(5), task.getRemindAt());
        assertEquals(TaskStatus.POSTPONED, task.getStatus());
        assertFalse(task.isCompleted());
    }

    @Test
    void declinedPostponementLeavesOriginalTaskUnchanged() {
        User user = user("postponeconflict");
        Task occupied = service.createTask(request(user, at(22, 3).plusMinutes(5)));
        Task original = service.createTask(request(user, at(23, 9)));

        assertThrows(TaskOverlapException.class, () -> service.postponeTask(original.getId(), PostponeOption.MINUTES_5, false));
        assertEquals(at(23, 9), original.getStartsAt());
        assertEquals(TaskStatus.PENDING, original.getStatus());
        service.postponeTask(original.getId(), PostponeOption.MINUTES_5, true);
        assertEquals(occupied.getStartsAt(), original.getStartsAt());
        assertEquals(2, tasks.count());
    }

    @Test
    void allEditableFieldsRemainUnchangedUntilConflictingEditIsConfirmed() {
        User first = user("oldowner");
        User second = user("newowner");
        Task original = service.createTask(request(first, at(24, 9)));
        service.createTask(request(second, at(23, 9)));
        Map<String, Object> changes = new HashMap<>(Map.of("name", "Edited", "description", "Edited description",
            "priority", "HIGH", "status", "POSTPONED", "startsAt", "2026-10-23T09:30:00",
            "endsAt", "2026-10-23T10:30:00", "dueDate", "2026-10-23T12:00:00",
            "user", Map.of("id", second.getId()), "repetition", "DAILY", "reminder", "CUSTOM"));
        changes.put("reminderAmount", 2);
        changes.put("reminderUnit", "DAYS");

        assertThrows(TaskOverlapException.class, () -> service.updateTask(original.getId(), changes, false));
        assertEquals("Task", original.getName());
        assertEquals(first.getId(), original.getUser().getId());
        assertEquals(Reminder.NONE, original.getReminder());
        assertEquals(at(24, 9), original.getStartsAt());
        service.updateTask(original.getId(), changes, true);
        assertEquals("Edited", original.getName());
        assertEquals(second.getId(), original.getUser().getId());
        assertEquals(Priority.HIGH, original.getPriority());
        assertEquals(TaskStatus.POSTPONED, original.getStatus());
        assertEquals(Repetition.DAILY, original.getRepetition());
        assertEquals(LocalDateTime.parse("2026-10-21T09:30:00"), original.getRemindAt());
        assertEquals(2, tasks.count());
    }

    @Test
    void generatedOccurrencesInheritReminderWithNewReminderTime() {
        Task request = request(user("repeatreminder"), at(23, 9));
        request.setReminder(Reminder.HOURS_1);
        request.setRepetition(Repetition.DAILY);
        Task source = service.createTask(request);
        repetition.generateDueOccurrences(at(24, 9));
        Task child = tasks.findAll().stream().filter(task -> source.getId().equals(task.getSourceTaskId())).findFirst().orElseThrow();
        assertEquals(Reminder.HOURS_1, child.getReminder());
        assertEquals(at(24, 8), child.getRemindAt());
        assertFalse(child.isCompleted());
    }

    private User user(String name) {
        return users.saveAndFlush(User.builder().name(name).username(name).email(name + "@example.com").build());
    }

    private Task request(User user, LocalDateTime start) {
        return Task.builder().name("Task").description("Description").user(user).priority(Priority.MEDIUM)
            .startsAt(start).endsAt(start == null ? null : start.plusHours(1))
            .dueDate(start == null ? at(23, 12) : start.plusHours(2)).build();
    }

    private LocalDateTime at(int day, int hour) { return LocalDateTime.of(2026, 10, day, hour, 0); }
}
