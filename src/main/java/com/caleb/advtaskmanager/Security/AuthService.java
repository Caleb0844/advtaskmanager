package com.caleb.advtaskmanager.Security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.caleb.advtaskmanager.Entities.User;
import com.caleb.advtaskmanager.Repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOCKOUT_MINUTES = 15;

    @Value("${auth.jwt.secret}")
    private String secret;

    @Value("${auth.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${auth.enabled:true}")
    private boolean enabled;

    @PostConstruct
    void validateConfiguration() {
        validateSecret(secret);
        if (!enabled) {
            LOG.warn("Authentication is disabled (auth.enabled=false); protected endpoints do not require JWT authentication");
        }
    }

    static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("auth.jwt.secret must be configured");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 characters long");
        }
    }

    public String authenticate(String username, String password) {
        LoginAttempt attempt = loginAttempts.get(username);
        Instant now = Instant.now();
        if (attempt != null && attempt.lockedUntil() != null && now.isBefore(attempt.lockedUntil())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many failed login attempts; try again later");
        }
        User user = userRepository.findByUsername(username)
            .orElse(null);
        if (user == null || user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            recordLoginFailure(username, now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        loginAttempts.remove(username);
        return tokenFor(user);
    }

    public boolean matchesPassword(String rawPassword, String passwordHash) {
        return passwordHash != null && passwordEncoder.matches(rawPassword, passwordHash);
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private void recordLoginFailure(String username, Instant now) {
        loginAttempts.compute(username, (key, previous) -> {
            int failures = previous == null ? 1 : previous.failures() + 1;
            Instant lockedUntil = failures >= MAX_LOGIN_FAILURES
                ? now.plusSeconds(LOCKOUT_MINUTES * 60) : null;
            return new LoginAttempt(failures, lockedUntil);
        });
    }

    private record LoginAttempt(int failures, Instant lockedUntil) {}

    public String tokenFor(User user) {
        long expiresAt = Instant.now().toEpochMilli() + expirationMs;
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode(write(Map.of("sub", user.getId().toString(), "exp", expiresAt)));
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    public Long userIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !MessageDigest.isEqual(sign(parts[0] + "." + parts[1])
                    .getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII))) return null;
            Map<?, ?> payload = JSON.readValue(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8), Map.class);
            long expiration = ((Number) payload.get("exp")).longValue();
            if (expiration < Instant.now().toEpochMilli()) return null;
            return Long.valueOf(payload.get("sub").toString());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public void requireCurrentUser(Long userId) {
        if (!enabled) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long currentId)
                || !currentId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this resource");
        }
    }

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof Long id ? id : null;
    }

    public Long scopedUserId(Long requestedUserId) {
        Long currentId = currentUserId();
        if (!enabled) return requestedUserId;
        if (currentId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        if (requestedUserId != null && !currentId.equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this resource");
        }
        return currentId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String write(Map<String, ?> value) {
        return JSON.writeValueAsString(value);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign authentication token", exception);
        }
    }
}
