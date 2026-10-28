package com.caleb.advtaskmanager.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;

import com.caleb.advtaskmanager.Enums.Priority;
import com.caleb.advtaskmanager.Enums.Repetition;
import com.caleb.advtaskmanager.Enums.TaskStatus;
import com.caleb.advtaskmanager.Enums.Reminder;
import com.caleb.advtaskmanager.Enums.ReminderUnit;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(name = "tasks")
@Setter 
@Getter 
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
@Builder

public class Task {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties("tasks")
    private User user;

    @NotBlank(message = "Task name is required")
    @Size(max = 255, message = "Task name must contain at most 255 characters")
    @Column (nullable = false)
    private String name;
 
    @NotBlank(message = "Task description is required")
    @Size(max = 255, message = "Task description must contain at most 255 characters")
    @Column (nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column (nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Transient
    @JsonIgnore
    private Boolean completionRequested;

    public boolean isCompleted() {
        return status == TaskStatus.COMPLETED;
    }

    @JsonProperty("completed")
    public void setCompleted(boolean completed) {
        completionRequested = completed;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'NONE'")
    @Builder.Default
    private Reminder reminder = Reminder.NONE;

    private Long reminderAmount;

    @Enumerated(EnumType.STRING)
    private ReminderUnit reminderUnit;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime remindAt;

    @Column (nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @Column (nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;

    @Column (nullable = false)
    private LocalDateTime dueDate;

    @NotNull(message = "Task startsAt is required")
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'NONE'")
    @Builder.Default
    private Repetition repetition = Repetition.NONE;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime nextOccurrenceAt;

    @JsonIgnore
    @Column(nullable = false)
    @ColumnDefault("0")
    private long nextOccurrenceNumber;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long sourceTaskId;


        @PrePersist
        protected void onCreate() {
            LocalDateTime now = LocalDateTime.now();
            createdAt = now;
            updatedAt = now;
            if (repetition == null) {
                repetition = Repetition.NONE;
            }
            if (status == null) status = TaskStatus.PENDING;
            if (reminder == null) reminder = Reminder.NONE;
        }

        @PreUpdate
        protected void onUpdate() {
            updatedAt = LocalDateTime.now();
        }


}
