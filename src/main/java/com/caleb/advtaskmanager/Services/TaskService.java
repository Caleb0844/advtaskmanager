package com.caleb.advtaskmanager.Services;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Entities.User;
import com.caleb.advtaskmanager.Enums.Priority;
import com.caleb.advtaskmanager.Enums.Repetition;
import com.caleb.advtaskmanager.Enums.TaskStatus;
import com.caleb.advtaskmanager.Enums.TaskView;
import com.caleb.advtaskmanager.Enums.PostponeOption;
import com.caleb.advtaskmanager.Repositories.TaskRepository;
import com.caleb.advtaskmanager.Exceptions.InvalidRequestException;
import com.caleb.advtaskmanager.Security.AuthService;
import tools.jackson.databind.json.JsonMapper;

import lombok.RequiredArgsConstructor;


@Service 
@RequiredArgsConstructor
@Transactional
public class TaskService {
    private final TaskRepository taskRepository;
    private final UserService userService;
    private final TaskScheduleService scheduleService;
    private final TaskRepetitionService repetitionService;
    private final TaskReminderService reminderService;
    private final TaskPendingService pendingService;
    private final Clock taskClock;
    private final AuthService authService;
    private static final JsonMapper UPDATE_MAPPER = JsonMapper.builder().build();
    private static final Set<String> EDITABLE_FIELDS = Set.of("name", "description", "priority", "status",
        "completed", "user", "dueDate", "startsAt", "endsAt", "repetition", "reminder", "reminderAmount", "reminderUnit");
    private static final Set<String> READ_ONLY_FIELDS = Set.of("id", "createdAt", "updatedAt", "remindAt",
        "nextOccurrenceAt", "sourceTaskId");

    public Task createTask(Task task){
        return createTask(task, false);
    }

    public Task createTask(Task task, boolean confirmOverlap) {
        if (task.getPriority() == null || task.getPriority() == Priority.NOT_SET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Task priority must be LOW, MEDIUM, or HIGH when creating a task");
        }
        if (task.getUser() == null || task.getUser().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Task must specify an existing user ID");
        }
        validateSchedule(task);
        User user = userService.getUserById(task.getUser().getId());
        authService.requireCurrentUser(user.getId());
        Task newTask = Task.builder()
            .name(task.getName())
            .description(task.getDescription())
            .priority(task.getPriority())
            .status(status(task))
            .dueDate(task.getDueDate())
            .startsAt(task.getStartsAt())
            .endsAt(task.getEndsAt())
            .repetition(repetition(task))
            .reminder(task.getReminder()).reminderAmount(task.getReminderAmount()).reminderUnit(task.getReminderUnit())
            .user(user)
            .build();
        repetitionService.configureNextOccurrence(newTask);
        reminderService.configure(newTask);
        scheduleService.checkForOverlap(newTask, user.getId(), null, confirmOverlap);
        user.addTask(newTask);
        return taskRepository.save(newTask);

    }
    public Task updateTask (Long id,Task task){
        return updateTask(id, task, false);
    }

    public Task updateTask(Long id, Task task, boolean confirmOverlap) {
        validateId(id);
        Task existing = taskRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        authService.requireCurrentUser(existing.getUser().getId());
        validateSchedule(task);
        if (task.getPriority() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task priority is required");
        }
        if (task.getUser() != null && task.getUser().getId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task must specify an existing user ID");
        User owner = task.getUser() == null ? existing.getUser() : userService.getUserById(task.getUser().getId());
        Task candidate = Task.builder()
            .id(existing.getId())
            .name(task.getName())
            .description(task.getDescription())
            .priority(task.getPriority())
            .status(status(task))
            .dueDate(task.getDueDate())
            .startsAt(task.getStartsAt())
            .endsAt(task.getEndsAt())
            .repetition(repetition(task))
            .reminder(task.getReminder()).reminderAmount(task.getReminderAmount()).reminderUnit(task.getReminderUnit())
            .user(owner)
            .sourceTaskId(existing.getSourceTaskId())
            .nextOccurrenceAt(existing.getNextOccurrenceAt())
            .nextOccurrenceNumber(existing.getNextOccurrenceNumber())
            .build();
        boolean promotedToSeries = candidate.getSourceTaskId() != null
            && candidate.getRepetition() != Repetition.NONE;
        if (promotedToSeries) {
            candidate.setSourceTaskId(null);
        }
        boolean anchorChanged = !Objects.equals(existing.getStartsAt(), candidate.getStartsAt())
            || !Objects.equals(existing.getDueDate(), candidate.getDueDate())
            || repetition(existing) != candidate.getRepetition();
        boolean ownerChanged = !existing.getUser().getId().equals(owner.getId());
        if (anchorChanged || promotedToSeries) {
            repetitionService.configureNextOccurrence(candidate);
        }
        reminderService.configure(candidate);
        if (anchorChanged || ownerChanged || promotedToSeries
                || (existing.isCompleted() && !candidate.isCompleted())
                || !Objects.equals(existing.getEndsAt(), candidate.getEndsAt())) {
            scheduleService.checkForOverlap(candidate, owner.getId(), id, confirmOverlap);
        }
        if (ownerChanged) {
            existing.getUser().getTasks().remove(existing);
            owner.addTask(existing);
        }
        existing.setName(candidate.getName());
        existing.setDescription(candidate.getDescription());
        existing.setPriority(candidate.getPriority());
        existing.setDueDate(candidate.getDueDate());
        existing.setStartsAt(candidate.getStartsAt());
        existing.setEndsAt(candidate.getEndsAt());
        existing.setRepetition(candidate.getRepetition());
        existing.setStatus(candidate.getStatus());
        existing.setNextOccurrenceAt(candidate.getNextOccurrenceAt());
        existing.setNextOccurrenceNumber(candidate.getNextOccurrenceNumber());
        existing.setSourceTaskId(candidate.getSourceTaskId());
        existing.setReminder(candidate.getReminder());
        existing.setReminderAmount(candidate.getReminderAmount());
        existing.setReminderUnit(candidate.getReminderUnit());
        existing.setRemindAt(candidate.getRemindAt());
        return taskRepository.save(existing);
    }
    
    public List<Task> getAllTasks(){
        return getTasks(null, TaskView.ALL);
    }

    public List<Task> getTasks(Long userId, TaskView view) {
        userId = authService.scopedUserId(userId);
        if (userId != null) userService.getUserById(userId);
        pendingService.refreshPendingTasks(LocalDateTime.now(taskClock));
        return taskRepository.findForList(userId, view.taskStatus());
    }

    public List<Task> getTodayTasks() {
        LocalDateTime now = LocalDateTime.now(taskClock);
        LocalDateTime start = now.toLocalDate().atStartOfDay();
        return taskRepository.findStartingBetween(authService.scopedUserId(null), start, start.plusDays(1));
    }

    public List<Task> getWeekTasks() {
        LocalDateTime now = LocalDateTime.now(taskClock);
        LocalDateTime start = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate().atStartOfDay();
        return taskRepository.findStartingBetween(authService.scopedUserId(null), start, start.plusWeeks(1));
    }

    public List<Task> getUpcomingTasks(int days) {
        if (days <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be positive");
        }
        LocalDateTime now = LocalDateTime.now(taskClock);
        return taskRepository.findUpcomingBetween(authService.scopedUserId(null), now, now.plusDays(days));
    }

    public List<Task> getOverdueTasks() {
        return taskRepository.findOverdue(authService.scopedUserId(null), LocalDateTime.now(taskClock));
    }

    public Task updateTask(Long id, Map<String, Object> fields, boolean confirmOverlap) {
        validateId(id);
        Map<String, String> errors = new LinkedHashMap<>();
        fields.keySet().stream().filter(key -> !EDITABLE_FIELDS.contains(key) && !READ_ONLY_FIELDS.contains(key))
            .forEach(key -> errors.put(key, "Unknown task field"));
        if (!errors.isEmpty()) throw new InvalidRequestException(errors);
        if (fields.keySet().stream().noneMatch(EDITABLE_FIELDS::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one editable task field");
        }
        if (fields.containsKey("completed") && !(fields.get("completed") instanceof Boolean)) {
            throw new InvalidRequestException(Map.of("completed", "completed must be true or false"));
        }
        Task existing = taskRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        authService.requireCurrentUser(existing.getUser().getId());
        Task parsed;
        try {
            parsed = UPDATE_MAPPER.convertValue(fields, Task.class);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid task update", exception);
        }
        Task merged = editableCopy(existing);
        if (fields.containsKey("name")) merged.setName(parsed.getName());
        if (fields.containsKey("description")) merged.setDescription(parsed.getDescription());
        if (fields.containsKey("priority")) merged.setPriority(parsed.getPriority());
        if (fields.containsKey("status")) merged.setStatus(parsed.getStatus());
        if (fields.containsKey("completed")) {
            if (parsed.getCompletionRequested() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "completed must be true or false");
            merged.setCompleted(parsed.getCompletionRequested());
        }
        if (fields.containsKey("dueDate")) merged.setDueDate(parsed.getDueDate());
        if (fields.containsKey("startsAt")) merged.setStartsAt(parsed.getStartsAt());
        if (fields.containsKey("endsAt")) merged.setEndsAt(parsed.getEndsAt());
        if (fields.containsKey("repetition")) merged.setRepetition(parsed.getRepetition());
        if (fields.containsKey("reminder")) merged.setReminder(parsed.getReminder());
        if (fields.containsKey("reminderAmount")) merged.setReminderAmount(parsed.getReminderAmount());
        if (fields.containsKey("reminderUnit")) merged.setReminderUnit(parsed.getReminderUnit());
        if (fields.containsKey("user")) {
            if (parsed.getUser() == null || parsed.getUser().getId() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task must specify an existing user ID");
            merged.setUser(parsed.getUser());
        }
        return updateTask(id, merged, confirmOverlap);
    }

    public Task postponeTask(Long id, PostponeOption option, boolean confirmOverlap) {
        validateId(id);
        if (option == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Postpone option is required");
        Task existing = taskRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        authService.requireCurrentUser(existing.getUser().getId());
        Task candidate = editableCopy(existing);
        LocalDateTime now = LocalDateTime.now(taskClock);
        LocalDateTime anchor = existing.getStartsAt() == null ? existing.getDueDate() : existing.getStartsAt();
        LocalDateTime newAnchor = option == PostponeOption.TOMORROW
            ? now.toLocalDate().plusDays(1).atTime(anchor.toLocalTime()) : now.plusMinutes(option.minutes);
        Duration shift = Duration.between(anchor, newAnchor);
        candidate.setDueDate(existing.getDueDate().plus(shift));
        if (existing.getStartsAt() != null) candidate.setStartsAt(existing.getStartsAt().plus(shift));
        if (existing.getEndsAt() != null) candidate.setEndsAt(existing.getEndsAt().plus(shift));
        candidate.setStatus(TaskStatus.POSTPONED);
        return updateTask(id, candidate, confirmOverlap);
    }

    private Task editableCopy(Task source) {
        return Task.builder().name(source.getName()).description(source.getDescription())
            .priority(source.getPriority()).status(source.getStatus()).user(source.getUser())
            .dueDate(source.getDueDate()).startsAt(source.getStartsAt()).endsAt(source.getEndsAt())
            .repetition(source.getRepetition()).reminder(source.getReminder())
            .reminderAmount(source.getReminderAmount()).reminderUnit(source.getReminderUnit()).build();
    }

    private TaskStatus status(Task task) {
        TaskStatus status = task.getStatus() == null ? TaskStatus.PENDING : task.getStatus();
        if (Boolean.TRUE.equals(task.getCompletionRequested())) return TaskStatus.COMPLETED;
        if (Boolean.FALSE.equals(task.getCompletionRequested()) && status == TaskStatus.COMPLETED)
            return TaskStatus.PENDING;
        return status;
    }
    public Task getTaskById(Long id){
        validateId(id);
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task with id " + id + " not found"));
        authService.requireCurrentUser(task.getUser().getId());
        return task;
    }
    public void deleteTask(Long id){ 
        validateId(id);
        Task task = taskRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task with id " + id + " not found"));
        authService.requireCurrentUser(task.getUser().getId());
        task.getUser().getTasks().remove(task);
        taskRepository.delete(task);
       }

    private Repetition repetition(Task task) {
        return task.getRepetition() == null ? Repetition.NONE : task.getRepetition();
    }

    private void validateSchedule(Task task) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateText(errors, "name", task.getName());
        validateText(errors, "description", task.getDescription());
        if (task.getDueDate() == null) {
            errors.put("dueDate", "Task dueDate is required");
        }
        if (task.getStartsAt() == null) {
            errors.put("startsAt", "Task startsAt is required");
        }
        if (task.getEndsAt() != null && (task.getStartsAt() == null
                || !task.getEndsAt().isAfter(task.getStartsAt()))) {
            errors.put("endsAt", "endsAt requires startsAt and must be after startsAt");
        }
        if (!errors.isEmpty()) throw new InvalidRequestException(errors);
    }

    private void validateText(Map<String, String> errors, String field, String value) {
        if (value == null || value.isBlank()) errors.put(field, "Task " + field + " is required");
        else if (value.length() > 255) errors.put(field, "Task " + field + " must contain at most 255 characters");
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task ID must be positive");
        }
    }
 
}
