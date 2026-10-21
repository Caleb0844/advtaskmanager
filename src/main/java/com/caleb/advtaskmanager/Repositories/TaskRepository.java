package com.caleb.advtaskmanager.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.caleb.advtaskmanager.Entities.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
    // Custom query methods can be defined here if needed

}
