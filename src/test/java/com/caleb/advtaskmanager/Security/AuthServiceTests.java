package com.caleb.advtaskmanager.Security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.caleb.advtaskmanager.Repositories.UserRepository;

import static org.mockito.Mockito.mock;

class AuthServiceTests {
    @Test
    void rejectsMissingJwtSecret() {
        assertThrows(IllegalStateException.class, () -> AuthService.validateSecret(" "));
    }

    @Test
    void rejectsJwtSecretShorterThan32Characters() {
        assertThrows(IllegalStateException.class, () -> AuthService.validateSecret("too-short"));
    }

    @Test
    void acceptsJwtSecretWithAtLeast32Characters() {
        assertDoesNotThrow(() -> AuthService.validateSecret("a-secret-that-is-at-least-32-chars"));
    }

    @Test
    void locksLoginAfterFiveFailedAttempts() {
        AuthService authService = new AuthService(mock(UserRepository.class));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(ResponseStatusException.class,
                () -> authService.authenticate("locked-user", "wrong-password"));
        }
        ResponseStatusException locked = assertThrows(ResponseStatusException.class,
            () -> authService.authenticate("locked-user", "wrong-password"));
        assertEquals(429, locked.getStatusCode().value());
    }
}
