package com.interviewmirror.domain.user.service;

import com.interviewmirror.domain.user.dto.UserCreateRequest;
import com.interviewmirror.domain.user.dto.UserResponse;
import com.interviewmirror.domain.user.dto.UserUpdateRequest;
import com.interviewmirror.domain.user.entity.User;
import com.interviewmirror.domain.user.repository.UserRepository;
import com.interviewmirror.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UserServiceImpl Tests")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private UserCreateRequest createRequest;
    private UserUpdateRequest updateRequest;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .passwordHash("$2a$10$encodedPassword")
                .createdAt(now)
                .updatedAt(now)
                .build();

        createRequest = UserCreateRequest.builder()
                .email("newuser@example.com")
                .name("New User")
                .password("password123")
                .build();

        updateRequest = UserUpdateRequest.builder()
                .name("Updated Name")
                .build();
    }

    @Test
    @DisplayName("Should create user successfully")
    void testCreateUserSuccess() {
        // Arrange
        String encodedPassword = "$2a$10$newEncodedPassword";
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(createRequest.getPassword())).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        UserResponse result = userService.createUser(createRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
        assertThat(result.getName()).isEqualTo(testUser.getName());

        // Verify password encoder was called
        verify(passwordEncoder, times(1)).encode(createRequest.getPassword());

        // Verify repository.save() was called
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(createRequest.getEmail());
        assertThat(savedUser.getName()).isEqualTo(createRequest.getName());
        assertThat(savedUser.getPasswordHash()).isEqualTo(encodedPassword);
    }

    @Test
    @DisplayName("Should throw exception when creating user with duplicate email")
    void testCreateUserDuplicateEmail() {
        // Arrange
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email already exists");

        // Verify password encoder was NOT called
        verify(passwordEncoder, never()).encode(anyString());

        // Verify repository.save() was NOT called
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should get user by id successfully")
    void testGetUserByIdSuccess() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        UserResponse result = userService.getUserById(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
        assertThat(result.getName()).isEqualTo(testUser.getName());
        assertThat(result.getCreatedAt()).isEqualTo(testUser.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(testUser.getUpdatedAt());

        // Verify repository was called
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when user not found by id")
    void testGetUserByIdNotFound() {
        // Arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");

        // Verify repository was called
        verify(userRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should get user by email successfully")
    void testGetUserByEmailSuccess() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // Act
        UserResponse result = userService.getUserByEmail(email);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getName()).isEqualTo(testUser.getName());

        // Verify repository was called
        verify(userRepository, times(1)).findByEmail(email);
    }

    @Test
    @DisplayName("Should throw exception when user not found by email")
    void testGetUserByEmailNotFound() {
        // Arrange
        String email = "notfound@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserByEmail(email))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");

        // Verify repository was called
        verify(userRepository, times(1)).findByEmail(email);
    }

    @Test
    @DisplayName("Should get all users successfully with multiple users")
    void testGetAllUsers() {
        // Arrange
        User user2 = User.builder()
                .id(2L)
                .email("user2@example.com")
                .name("User Two")
                .passwordHash("$2a$10$encodedPassword2")
                .createdAt(now)
                .updatedAt(now)
                .build();

        User user3 = User.builder()
                .id(3L)
                .email("user3@example.com")
                .name("User Three")
                .passwordHash("$2a$10$encodedPassword3")
                .createdAt(now)
                .updatedAt(now)
                .build();

        List<User> users = Arrays.asList(testUser, user2, user3);
        when(userRepository.findAll()).thenReturn(users);

        // Act
        List<UserResponse> results = userService.getAllUsers();

        // Assert
        assertThat(results).isNotNull();
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getId()).isEqualTo(1L);
        assertThat(results.get(0).getEmail()).isEqualTo("test@example.com");
        assertThat(results.get(1).getId()).isEqualTo(2L);
        assertThat(results.get(1).getEmail()).isEqualTo("user2@example.com");
        assertThat(results.get(2).getId()).isEqualTo(3L);
        assertThat(results.get(2).getEmail()).isEqualTo("user3@example.com");

        // Verify repository was called
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should return empty list when no users exist")
    void testGetAllUsersEmpty() {
        // Arrange
        when(userRepository.findAll()).thenReturn(Arrays.asList());

        // Act
        List<UserResponse> results = userService.getAllUsers();

        // Assert
        assertThat(results).isNotNull();
        assertThat(results).isEmpty();

        // Verify repository was called
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should update user successfully")
    void testUpdateUserSuccess() {
        // Arrange
        Long userId = 1L;
        User updatedUserEntity = User.builder()
                .id(userId)
                .email(testUser.getEmail())
                .name(updateRequest.getName())
                .passwordHash(testUser.getPasswordHash())
                .createdAt(testUser.getCreatedAt())
                .updatedAt(now)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUserEntity);

        // Act
        UserResponse result = userService.updateUser(userId, updateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getName()).isEqualTo(updateRequest.getName());
        assertThat(result.getEmail()).isEqualTo(testUser.getEmail());

        // Verify repository interactions
        verify(userRepository, times(1)).findById(userId);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getName()).isEqualTo(updateRequest.getName());
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent user")
    void testUpdateUserNotFound() {
        // Arrange
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateUser(userId, updateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");

        // Verify repository was called for find, but not for save
        verify(userRepository, times(1)).findById(userId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should delete user successfully")
    void testDeleteUserSuccess() {
        // Arrange
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);

        // Act
        userService.deleteUser(userId);

        // Assert - no exception thrown
        // Verify repository interactions
        verify(userRepository, times(1)).existsById(userId);
        verify(userRepository, times(1)).deleteById(userId);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void testDeleteUserNotFound() {
        // Arrange
        Long userId = 999L;
        when(userRepository.existsById(userId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");

        // Verify repository was called for exists check, but not for delete
        verify(userRepository, times(1)).existsById(userId);
        verify(userRepository, never()).deleteById(anyLong());
    }
}
