package com.caleb.advtaskmanager.Controllers;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Services.TaskService;
import com.caleb.advtaskmanager.Enums.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;




@RestController 
@RequestMapping ("/api/tasks")
@RequiredArgsConstructor 

public class TaskController { 


    private final TaskService taskService;

    @PostMapping()
    public Task createTask(@Valid @RequestBody Task task,
            @RequestParam(defaultValue = "false") boolean confirmOverlap){
        return taskService.createTask(task, confirmOverlap);

    }
    @PutMapping("/{id}")
    public Task updateTask(@PathVariable @Positive Long id,@RequestBody Map<String, Object> task,
            @RequestParam(defaultValue = "false") boolean confirmOverlap){
        return  taskService.updateTask(id, task, confirmOverlap);
    }
    @GetMapping
    public List<Task> getAllTasks(@RequestParam(required = false) @Positive Long userId,
            @RequestParam(defaultValue = "ALL") TaskView status){
        return taskService.getTasks(userId, status);
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("statuses", TaskStatus.values(), "views", TaskView.values(),
            "repetitions", Repetition.values(), "priorities", List.of(Priority.LOW, Priority.MEDIUM, Priority.HIGH),
            "reminders", Arrays.stream(Reminder.values())
                .map(value -> new Choice(value.name(), value.label)).toList(),
            "reminderUnits", ReminderUnit.values(),
            "postponeOptions", Arrays.stream(PostponeOption.values())
                .map(value -> new Choice(value.name(), value.label)).toList());
    }

    @GetMapping("/today")
    public List<Task> today() {
        return taskService.getTodayTasks();
    }

    @GetMapping("/week")
    public List<Task> week() {
        return taskService.getWeekTasks();
    }

    @GetMapping("/upcoming")
    public List<Task> upcoming(@RequestParam(defaultValue = "7") int days) {
        return taskService.getUpcomingTasks(days);
    }

    @GetMapping("/overdue")
    public List<Task> overdue() {
        return taskService.getOverdueTasks();
    }

    @PutMapping("/{id}/postpone")
    public Task postponeTask(@PathVariable @Positive Long id, @RequestBody PostponeRequest request,
            @RequestParam(defaultValue = "false") boolean confirmOverlap) {
        return taskService.postponeTask(id, request.option(), confirmOverlap);
    }

    public record PostponeRequest(PostponeOption option) {}
    public record Choice(String value, String label) {}
    @GetMapping ("/{id}")
    public Task getTaskById(@PathVariable @Positive Long id){
        return taskService.getTaskById(id);
    }
    @DeleteMapping ("/{id}")
    public void deleteTask(@PathVariable @Positive Long id){
        taskService.deleteTask(id);
      }
 

}
