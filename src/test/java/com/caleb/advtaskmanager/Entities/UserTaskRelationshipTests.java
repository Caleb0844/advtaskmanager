package com.caleb.advtaskmanager.Entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.caleb.advtaskmanager.Enums.Priority;
import com.caleb.advtaskmanager.Enums.TaskStatus;
import com.caleb.advtaskmanager.Repositories.TaskRepository;
import com.caleb.advtaskmanager.Repositories.UserRepository;
import com.caleb.advtaskmanager.Services.TaskService;
import com.caleb.advtaskmanager.SchedulingTestConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(SchedulingTestConfiguration.class)
class UserTaskRelationshipTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskService taskService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void userHasManyTasksAndEachTaskHasItsOwnUser() {
        User first = saveUser("first");
        User second = saveUser("second");
        Task firstTask = taskService.createTask(taskRequest(first));
        Task secondTask = taskService.createTask(taskRequest(first));
        Task otherTask = taskService.createTask(taskRequest(second));
        entityManager.flush();
        entityManager.clear();

        User loadedFirst = userRepository.findById(first.getId()).orElseThrow();
        User loadedSecond = userRepository.findById(second.getId()).orElseThrow();

        assertEquals(2, loadedFirst.getTasks().size());
        assertEquals(List.of(firstTask.getId(), secondTask.getId()).stream().sorted().toList(),
            loadedFirst.getTasks().stream().map(Task::getId).sorted().toList());
        assertEquals(List.of(otherTask.getId()), loadedSecond.getTasks().stream().map(Task::getId).toList());
        for (Task task : loadedFirst.getTasks()) {
            assertEquals(first.getId(), task.getUser().getId());
        }
        assertEquals(second.getId(), loadedSecond.getTasks().getFirst().getUser().getId());
    }

    @Test
    void creationRequiresUserId() {
        for (User user : new User[] {null, User.builder().build()}) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> taskService.createTask(taskRequest(user)));

            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        assertEquals(0, taskRepository.count());
    }

    @Test
    void creationRejectsUnknownUser() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> taskService.createTask(taskRequest(User.builder().id(Long.MAX_VALUE).build())));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals(0, taskRepository.count());
    }

    @Test
    void databaseRejectsTaskWithoutOwner() {
        assertThrows(DataIntegrityViolationException.class,
            () -> taskRepository.saveAndFlush(taskRequest(null)));
    }

    @Test
    void userAndTaskJsonAvoidRecursionAndAcceptUserId() {
        User user = saveUser("json");
        Task task = taskService.createTask(taskRequest(user));
        entityManager.flush();
        entityManager.clear();
        user = userRepository.findById(user.getId()).orElseThrow();
        task = taskRepository.findById(task.getId()).orElseThrow();
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode profileJson = mapper.valueToTree(user);
        JsonNode taskJson = mapper.valueToTree(task);

        assertEquals(1, profileJson.get("tasks").size());
        assertFalse(profileJson.get("tasks").get(0).has("user"));
        assertEquals(user.getId().longValue(), taskJson.get("user").get("id").asLong());
        assertFalse(taskJson.get("user").has("tasks"));
        Task parsed = mapper.readValue("{\"user\":{\"id\":1},\"priority\":\"HIGH\"}", Task.class);
        assertEquals(1L, parsed.getUser().getId());
    }

    @Test
    void storesPriorityUsingEnumName() {
        Task task = taskService.createTask(taskRequest(saveUser("legacy")));
        entityManager.flush();
        assertEquals("HIGH", entityManager.createNativeQuery("select priority from tasks where id = :id")
            .setParameter("id", task.getId()).getSingleResult());
    }

    @Test
    void suppliedTaskIdCannotOverwriteAnotherUsersTask() {
        User first = saveUser("original");
        User second = saveUser("new");
        Task original = taskService.createTask(taskRequest(first));
        Task request = taskRequest(second);
        request.setId(original.getId());

        Task created = taskService.createTask(request);
        entityManager.flush();
        entityManager.clear();

        assertNotEquals(original.getId(), created.getId());
        assertEquals(first.getId(), taskRepository.findById(original.getId()).orElseThrow().getUser().getId());
        assertEquals(second.getId(), taskRepository.findById(created.getId()).orElseThrow().getUser().getId());
    }

    @Test
    void creationStillRequiresSelectablePriority() {
        User user = saveUser("priority");
        for (Priority priority : new Priority[] {null, Priority.NOT_SET}) {
            Task request = taskRequest(user);
            request.setPriority(priority);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> taskService.createTask(request));

            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        assertEquals(0, taskRepository.count());
    }

    private User saveUser(String username) {
        return userRepository.saveAndFlush(User.builder()
            .name(username)
            .username(username)
            .email(username + "@example.com")
            .build());
    }

    private Task taskRequest(User user) {
        return Task.builder()
            .name("Finish report")
            .description("Complete the weekly report")
            .priority(Priority.HIGH)
            .status(TaskStatus.PENDING)
            .dueDate(LocalDateTime.of(2026, 10, 23, 17, 0))
            .user(user)
            .build();
    }
}
