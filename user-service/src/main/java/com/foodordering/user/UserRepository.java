package com.foodordering.user;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
