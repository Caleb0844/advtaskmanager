package com.caleb.advtaskmanager;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.caleb.advtaskmanager.Controllers.TaskExceptionHandler;

class TaskExceptionHandlerTests {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailureProbe())
        .setControllerAdvice(new TaskExceptionHandler()).build();

    @ParameterizedTest
    @CsvSource({"database,503", "constraint,409", "lock,409", "query,500", "unexpected,500"})
    void failureResponsesHideInternalDetailsAndPreserveCorrectStatus(String kind, int expected) throws Exception {
        mvc.perform(get("/test-errors/{kind}", kind))
            .andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
            .andExpect(jsonPath("$.path").value("/test-errors/" + kind))
            .andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.confirmationRequired").doesNotExist())
            .andExpect(content().string(not(containsString("private database details"))));
    }

    @RestController
    static class FailureProbe {
        @GetMapping("/test-errors/{kind}")
        String fail(@PathVariable String kind) {
            throw switch (kind) {
                case "database" -> new DataAccessResourceFailureException("private database details");
                case "constraint" -> new DataIntegrityViolationException("private database details");
                case "lock" -> new CannotAcquireLockException("private database details");
                case "query" -> new InvalidDataAccessResourceUsageException("private database details");
                default -> new IllegalStateException("private database details");
            };
        }
    }
}
