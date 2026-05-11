package com.interviewmirror.user.controller;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.user.dto.UserCreateRequest;
import com.interviewmirror.user.dto.UserResponse;
import com.interviewmirror.user.dto.UserUpdateRequest;
import com.interviewmirror.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
    UserResponse response = userService.createUser(request);
    return ApiResponse.success(response);
  }

  @GetMapping("/{id}")
  public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
    UserResponse response = userService.getUserById(id);
    return ApiResponse.success(response);
  }

  @GetMapping("/email/{email}")
  public ApiResponse<UserResponse> getUserByEmail(@PathVariable String email) {
    UserResponse response = userService.getUserByEmail(email);
    return ApiResponse.success(response);
  }

  @GetMapping
  public ApiResponse<List<UserResponse>> getAllUsers() {
    List<UserResponse> responses = userService.getAllUsers();
    return ApiResponse.success(responses);
  }

  @PutMapping("/{id}")
  public ApiResponse<UserResponse> updateUser(
      @PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
    UserResponse response = userService.updateUser(id, request);
    return ApiResponse.success(response);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUser(@PathVariable Long id) {
    userService.deleteUser(id);
  }
}
