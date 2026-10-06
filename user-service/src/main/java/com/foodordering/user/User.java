package com.foodordering.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;
    @Column(nullable = false)
    private String name;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(nullable = false)
    private String password;
    private String phone;
    @Enumerated(EnumType.STRING)
    private Role role;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String x) {
        name = x;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String x) {
        password = x;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String x) {
        phone = x;
    }

    public Role getRole() {
        return role;
    }

    public static User make(String n, String e, String p, String ph, Role r) {
        User u = new User();
        u.name = n;
        u.email = e;
        u.password = p;
        u.phone = ph;
        u.role = r;
        return u;
    }
}

enum Role {
    CUSTOMER, ADMIN
}
