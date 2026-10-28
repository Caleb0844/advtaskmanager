package com.caleb.advtaskmanager.Entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @NotBlank(message = "User name is required")
    @Size(max = 255, message = "User name must contain at most 255 characters")
    @Column(nullable = false, unique = false)
    private String name;

    @NotBlank(message = "Username is required")
    @Size(max = 255, message = "Username must contain at most 255 characters")
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @NotBlank(message = "User email is required")
    @Email(message = "User email must be valid")
    @Size(max = 255, message = "User email must contain at most 255 characters")
    @Column(nullable = false)
    private String email;

    @JsonIgnore
    @Column
    private String passwordHash;

    @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY)
    @jakarta.persistence.Transient
    private String password;

    @OneToMany(mappedBy = "user")
    @OrderBy("dueDate ASC, id ASC")
    @JsonIgnoreProperties("user")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;

    public void addTask(Task task) {
        tasks.add(task);
        task.setUser(this);
    }

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
