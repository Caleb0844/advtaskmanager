package com.caleb.advtaskmanager.Services;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.caleb.advtaskmanager.Entities.User;
import com.caleb.advtaskmanager.Repositories.UserRepository;
import com.caleb.advtaskmanager.Security.AuthService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
        }
        if (authService.isEnabled() && (user.getPassword() == null || user.getPassword().length() < 8)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters");
        }
        User newUser = User.builder()
            .name(user.getName())
            .username(user.getUsername())
            .email(user.getEmail())
            .passwordHash(user.getPassword() == null ? null : passwordEncoder.encode(user.getPassword()))
            .build();
        return userRepository.save(newUser);
    }

    public User updateUser(Long id, User user) {
        User existingUser = getUserById(id);
        authService.requireCurrentUser(id);
        if (userRepository.existsByUsernameAndIdNot(user.getUsername(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
        }
        existingUser.setName(user.getName());
        existingUser.setUsername(user.getUsername());
        existingUser.setEmail(user.getEmail());
        if (user.getPassword() != null) {
            if (user.getPassword().length() < 8)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters");
            existingUser.setPasswordHash(passwordEncoder.encode(user.getPassword()));
        }
        return userRepository.save(existingUser);
    }

    public User getCurrentUser() {
        Long currentId = authService.currentUserId();
        if (currentId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return getUserById(currentId);
    }

    public User changeCurrentPassword(String currentPassword, String newPassword) {
        User user = getCurrentUser();
        if (!authService.matchesPassword(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPasswordHash(authService.encodePassword(newPassword));
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        Long currentId = authService.currentUserId();
        return currentId == null ? userRepository.findAll() : List.of(getUserById(currentId));
    }

    public User getUserById(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID must be positive");
        }
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User with id " + id + " not found"));
        authService.requireCurrentUser(id);
        return user;
    }

    public void deleteUser(Long id) {
        User user = getUserById(id);
        if (!user.getTasks().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This user still has tasks. Delete or reassign their tasks before deleting the user.");
        }
        userRepository.delete(user);
    }
}
