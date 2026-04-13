package com.interviewmirror.domain.user.repository;

import com.interviewmirror.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("should save user successfully")
    void testSaveUser() {
        // Arrange
        User user = User.builder()
                .email("test@example.com")
                .name("Test User")
                .passwordHash("hashedPassword123")
                .build();

        // Act
        User savedUser = userRepository.save(user);
        entityManager.flush();

        // Assert
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getName()).isEqualTo("Test User");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashedPassword123");
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should find user by email when user exists")
    void testFindByEmail() {
        // Arrange
        User user = User.builder()
                .email("findme@example.com")
                .name("Find Me User")
                .passwordHash("hashedPassword456")
                .build();

        entityManager.persistAndFlush(user);
        entityManager.clear();

        // Act
        Optional<User> foundUser = userRepository.findByEmail("findme@example.com");

        // Assert
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("findme@example.com");
        assertThat(foundUser.get().getName()).isEqualTo("Find Me User");
    }

    @Test
    @DisplayName("should return empty when user not found by email")
    void testFindByEmailNotFound() {
        // Act
        Optional<User> foundUser = userRepository.findByEmail("nonexistent@example.com");

        // Assert
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("should return true when user exists by email")
    void testExistsByEmail() {
        // Arrange
        User user = User.builder()
                .email("exists@example.com")
                .name("Exists User")
                .passwordHash("hashedPassword789")
                .build();

        entityManager.persistAndFlush(user);
        entityManager.clear();

        // Act
        boolean exists = userRepository.existsByEmail("exists@example.com");

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("should return false when user does not exist by email")
    void testExistsByEmailNotFound() {
        // Act
        boolean exists = userRepository.existsByEmail("notfound@example.com");

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("should delete user by id successfully")
    void testDeleteById() {
        // Arrange
        User user = User.builder()
                .email("delete@example.com")
                .name("Delete User")
                .passwordHash("hashedPasswordDelete")
                .build();

        entityManager.persistAndFlush(user);
        Long userId = user.getId();
        entityManager.clear();

        // Act
        userRepository.deleteById(userId);
        entityManager.clear();

        // Assert
        Optional<User> deletedUser = userRepository.findById(userId);
        assertThat(deletedUser).isEmpty();
    }
}
