package com.caleb.advtaskmanager.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.caleb.advtaskmanager.Entities.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    boolean existsByUsernameAndIdNot(String username, Long id);
    Optional<User> findByUsername(String username);
}
