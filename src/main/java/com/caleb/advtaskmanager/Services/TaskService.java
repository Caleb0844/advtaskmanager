package com.caleb.advtaskmanager.Services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Repositories.TaskRepository;

import lombok.RequiredArgsConstructor;


@Service 
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;

    public Task createTask(Task task){
        return taskRepository.save(task);

    }
    public Task updateTask (Long id,Task task){
        Task existTask = taskRepository.findById(id)
            .orElseThrow(() ->new RuntimeException("Task not found"));
            existTask.setName(task.getName());
            existTask.setDescription(task.getDescription());
            existTask.setPriority(task.getPriority());
            existTask.setDueDate(task.getDueDate());
            existTask.setStatus(task.getStatus());
        return  taskRepository.save(existTask);
    }
    
    public List<Task> getAllTasks(){
        return taskRepository.findAll();
    }
    public Task getTaskById(Long id){
        return taskRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Task with id " +id + " not found"));
    }
    public void deleteTask(Long id){ 
        taskRepository.deleteById(id);
       }
 
}
