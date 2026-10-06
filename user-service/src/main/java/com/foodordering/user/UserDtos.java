package com.foodordering.user;

import jakarta.validation.constraints.*;

record RegisterRequest(@NotBlank String name, @Email @NotBlank String email, @Size(min = 8) String password,
        @NotBlank String phone, String role) {
}

record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
}

record UpdateUserRequest(String name, String phone, @Size(min = 8) String password) {
}

record UserResponse(Long customerId, String name, String email, String phone, String role) {
}

record LoginResponse(String token, String tokenType, long expiresIn, Long userId, String role) {
}
