package com.interviewmirror.domain.user.service;

import com.interviewmirror.domain.user.dto.UserCreateRequest;
import com.interviewmirror.domain.user.dto.UserResponse;
import com.interviewmirror.domain.user.dto.UserUpdateRequest;
import com.interviewmirror.domain.user.entity.User;
import com.interviewmirror.domain.user.repository.UserRepository;
import com.interviewmirror.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional
    @CacheEvict(value = {"user", "users"}, allEntries = true)
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists", "DUPLICATE_EMAIL");
        }

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created: {}", savedUser.getId());

        return mapToResponse(savedUser);
    }

    @Override
    @Cacheable(value = "user", key = "#id")
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        return mapToResponse(user);
    }

    @Override
    @Cacheable(value = "user", key = "#email")
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        return mapToResponse(user);
    }

    @Override
    @Cacheable(value = "users")
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"user", "users"}, allEntries = true)
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));

        user.setName(request.getName());
        User updatedUser = userRepository.save(user);
        log.info("User updated: {}", updatedUser.getId());

        return mapToResponse(updatedUser);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"user", "users"}, allEntries = true)
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException("User not found", "USER_NOT_FOUND");
        }
        userRepository.deleteById(id);
        log.info("User deleted: {}", id);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
