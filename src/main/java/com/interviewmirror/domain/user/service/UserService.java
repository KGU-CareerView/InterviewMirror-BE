package com.interviewmirror.domain.user.service;

import com.interviewmirror.domain.user.dto.UserCreateRequest;
import com.interviewmirror.domain.user.dto.UserResponse;
import com.interviewmirror.domain.user.dto.UserUpdateRequest;

import java.util.List;

public interface UserService {
    UserResponse createUser(UserCreateRequest request);
    UserResponse getUserById(Long id);
    UserResponse getUserByEmail(String email);
    List<UserResponse> getAllUsers();
    UserResponse updateUser(Long id, UserUpdateRequest request);
    void deleteUser(Long id);
}
