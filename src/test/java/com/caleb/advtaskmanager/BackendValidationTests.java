package com.caleb.advtaskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Entities.User;
import com.caleb.advtaskmanager.Enums.Priority;
import com.caleb.advtaskmanager.Repositories.TaskRepository;
import com.caleb.advtaskmanager.Repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TaskClockTestConfiguration.class)
@Transactional
class BackendValidationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TaskRepository tasks;

    @Test
    void newlyCreatedUserCanImmediatelyAddTheirFirstTask() throws Exception {
        String created = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"New user\",\"username\":\"newuser\",\"email\":\"newuser@example.com\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long userId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created).get("id").asLong();
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"First task","description":"Created through the API","priority":"HIGH",
             "user":{"id":%d},"dueDate":"2026-10-23T12:00:00","startsAt":"2026-10-23T11:00:00"}
            """.formatted(userId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(userId));
        mvc.perform(get("/api/users/{id}", userId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks.length()").value(1));
    }

    @Test
    void missingResourcesUseConsistentNotFoundErrorsIncludingDelete() throws Exception {
        for (String path : new String[]{"/api/tasks/999999", "/api/users/999999", "/api/missing"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
        }
        mvc.perform(delete("/api/tasks/999999")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/999999")).andExpect(status().isNotFound());
    }

    @Test
    void malformedAndNonPositiveIdsAndInvalidFiltersAreBadRequests() throws Exception {
        for (String path : new String[]{"/api/tasks/abc", "/api/tasks/0", "/api/users/-1",
                "/api/tasks?userId=0", "/api/tasks?status=Started", "/api/tasks?confirmOverlap=invalid&userId=abc"}) {
            mvc.perform(get(path)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Test
    void userValidationIncludesFieldErrors() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\" \",\"username\":\"\",\"email\":\"invalid\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists())
            .andExpect(jsonPath("$.fieldErrors.username").exists()).andExpect(jsonPath("$.fieldErrors.email").exists());
        assertEquals(0, users.count());
    }

    @Test
    void duplicateUsernameIsAConflict() throws Exception {
        user("taken");
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"New\",\"username\":\"taken\",\"email\":\"new@example.com\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Username is already in use"))
            .andExpect(jsonPath("$.confirmationRequired").doesNotExist());
        assertEquals(1, users.count());
    }

    @Test
    void updatingToDuplicateUsernameLeavesOriginalUserIntact() throws Exception {
        user("taken");
        User original = user("original");
        mvc.perform(put("/api/users/{id}", original.getId()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Changed\",\"username\":\"taken\",\"email\":\"new@example.com\"}"))
            .andExpect(status().isConflict());
        assertEquals("original", users.findById(original.getId()).orElseThrow().getUsername());
        assertEquals("original", original.getName());
    }

    @Test
    void deletingUserWithTasksReturnsConflictAndPreservesTheirTasks() throws Exception {
        User owner = user("owner");
        Task task = task(owner);
        mvc.perform(delete("/api/users/{id}", owner.getId()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(
                "This user still has tasks. Delete or reassign their tasks before deleting the user."));
        assertTrue(tasks.existsById(task.getId()));
        assertTrue(users.existsById(owner.getId()));
    }

    @Test
    void deletingLastTaskUpdatesProfileAndAllowsDeletingOwner() throws Exception {
        User owner = user("owner");
        Task task = task(owner);
        mvc.perform(delete("/api/tasks/{id}", task.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/users/{id}", owner.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks.length()").value(0));
        mvc.perform(delete("/api/users/{id}", owner.getId())).andExpect(status().isOk());
        assertEquals(0, tasks.count());
        assertEquals(0, users.count());
    }

    static Stream<String> invalidUpdates() {
        return Stream.of("{\"name\":\" \"}", "{\"description\":null}", "{\"name\":\"" + "a".repeat(256) + "\"}",
            "{\"dueDate\":null}", "{\"completed\":\"yes\"}", "{\"priority\":\"URGENT\"}",
            "{\"dueDate\":\"bad-date\"}", "{\"nmae\":\"typo\"}", "{}", "{\"id\":123}");
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void rejectedEditsPreserveEverySavedField(String body) throws Exception {
        Task task = task(user("editor"));
        mvc.perform(put("/api/tasks/{id}", task.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        Task saved = tasks.findById(task.getId()).orElseThrow();
        assertEquals("Original", saved.getName());
        assertEquals("Description", saved.getDescription());
        assertEquals(LocalDateTime.of(2026, 10, 23, 12, 0), saved.getDueDate());
        assertEquals(Priority.MEDIUM, saved.getPriority());
    }

    @Test
    void malformedMissingAndQuotedJsonBodiesReturnClearBadRequest() throws Exception {
        for (String body : new String[]{"{", "", "\"not a JSON object\"", "[]"}) {
            mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                    org.hamcrest.Matchers.containsString("JSON object")));
        }
    }

    @Test
    void frameworkErrorsKeepTheirHttpStatusAndHeaders() throws Exception {
        mvc.perform(patch("/api/users/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"))
            .andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/api/users").contentType(MediaType.TEXT_PLAIN).content("{}"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    private User user(String username) {
        return users.saveAndFlush(User.builder().name(username).username(username).email(username + "@example.com").build());
    }

    private Task task(User owner) {
        Task task = Task.builder().name("Original").description("Description").priority(Priority.MEDIUM)
            .user(owner).startsAt(LocalDateTime.of(2026, 10, 23, 11, 0))
            .dueDate(LocalDateTime.of(2026, 10, 23, 12, 0)).build();
        owner.addTask(task);
        return tasks.saveAndFlush(task);
    }
}
