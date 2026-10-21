package com.caleb.advtaskmanager.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(name = "tasks")
@Setter 
@Getter 
@NoArgsConstructor 
@AllArgsConstructor
@Builder

public class Task {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // @NotBlank(message ="Tasknaame is required")
    @Column (nullable = false)
    private String name;
 
   // @NotBlank(message = "Task description is required")
    @Column (nullable = false)
    private String description;

  //  @NotBlank(message = "Task priority is required")
    @Column (nullable = false)
    private String priority;

    @Column (nullable = false)
    private String status;

    @Column (nullable = false)
    private LocalDateTime createdAt;

    @Column (nullable = false)
    private LocalDateTime updatedAt;

    @Column (nullable = false)
    private LocalDateTime dueDate;


        @PrePersist
        protected void onCreate() {
            LocalDateTime now = LocalDateTime.now();
            createdAt = now;
            updatedAt = now;
        }

        @PreUpdate
        protected void onUpdate() {
            updatedAt = LocalDateTime.now();
        }


}

    //ToDo Relations
