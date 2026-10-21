package com.caleb.advtaskmanager.Controllers;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Services.TaskService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;




@RestController 
@RequestMapping ("/api/tasks")
@RequiredArgsConstructor 

public class TaskController { 


    private final TaskService taskService;

    @PostMapping()
    public Task createTask(@Valid @RequestBody Task task){
        return taskService.createTask(task);

    }
    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id,@Valid @RequestBody Task task){
        return  taskService.updateTask(id, task);
    }
    @GetMapping
    public List<Task> getAllTasks(){
        return taskService.getAllTasks();
    }
    @GetMapping ("/{id}")
    public Task getTaskById(@PathVariable Long id){
        return taskService.getTaskById(id);
    }
    @DeleteMapping ("/{id}")
    public void deleteTask(@PathVariable Long id){  
        taskService.deleteTask(id);
      }
 

}
