package com.interviewmirror.user.service;

import com.interviewmirror.user.dto.UserCreateRequest;
import com.interviewmirror.user.dto.UserResponse;
import com.interviewmirror.user.dto.UserUpdateRequest;
import java.util.List;

public interface UserService {
  UserResponse createUser(UserCreateRequest request);

  UserResponse getUserById(Long id);

  UserResponse getUserByEmail(String email);

  List<UserResponse> getAllUsers();

  UserResponse updateUser(Long id, UserUpdateRequest request);

  void deleteUser(Long id);
}
