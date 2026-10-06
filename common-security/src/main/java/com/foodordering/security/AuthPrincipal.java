package com.foodordering.security;
public record AuthPrincipal(Long userId, String email, String role) {}
