package com.caleb.advtaskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Entities.User;

import tools.jackson.databind.json.JsonMapper;

class UserJsonTests {
    @Test
    void rootUserThenNestedTaskOwnerCanBeReadByTheSameMapper() {
        JsonMapper mapper = JsonMapper.builder().build();
        User user = mapper.readValue("""
            {"name":"Verification","username":"verification","email":"verification@example.com"}
            """, User.class);
        Task task = mapper.readValue("""
            {"name":"Task","description":"Description","priority":"HIGH", "user":{"id":1},
             "dueDate":"2026-10-23T12:00:00"}
            """, Task.class);
        assertEquals("verification", user.getUsername());
        assertEquals(1L, task.getUser().getId());
    }

    @Test
    void nestedTaskOwnerThenRootUserCanBeReadByTheSameMapper() {
        JsonMapper mapper = JsonMapper.builder().build();
        Task task = mapper.readValue("{\"user\":{\"id\":1}}", Task.class);
        User user = mapper.readValue("{\"name\":\"Verification\",\"username\":\"verification\",\"email\":\"verification@example.com\"}", User.class);
        assertEquals(1L, task.getUser().getId());
        assertEquals("verification", user.getUsername());
    }

    @Test
    void serializedUsersNeverExposePasswordHashOrPassword() {
        JsonMapper mapper = JsonMapper.builder().build();
        User user = User.builder().name("Secret").username("secret").email("secret@example.com")
            .passwordHash("stored-hash").password("raw-password").build();
        String json = mapper.writeValueAsString(user);
        assertFalse(json.contains("stored-hash"));
        assertFalse(json.contains("raw-password"));
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("\"password\""));
    }
}
