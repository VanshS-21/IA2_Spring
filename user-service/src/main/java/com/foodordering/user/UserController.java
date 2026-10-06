package com.foodordering.user;

import com.foodordering.security.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UserController {
    private final UserRepository r;
    private final PasswordEncoder pe;
    private final JwtUtil jwt;
    private final AuthenticationManager authenticationManager;

    UserController(UserRepository r, PasswordEncoder pe, JwtUtil jwt, AuthenticationManager authenticationManager) {
        this.r = r;
        this.pe = pe;
        this.jwt = jwt;
        this.authenticationManager = authenticationManager;
    }

    private UserResponse dto(User u) {
        return new UserResponse(u.getCustomerId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole().name());
    }

    @PostMapping("/users/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest x) {
        if (r.findByEmail(x.email()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        return dto(r.save(User.make(x.name(), x.email(), pe.encode(x.password()), x.phone(), Role.CUSTOMER)));
    }

    @PostMapping("/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest x) {
        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(x.email(), x.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials");
        }
        User u = r.findByEmail(x.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials"));
        return new LoginResponse(jwt.generate(u.getEmail(), u.getCustomerId(), u.getRole().name()), "Bearer",
                jwt.expiration(), u.getCustomerId(), u.getRole().name());
    }

    @GetMapping("/users/me")
    UserResponse me(Authentication a) {
        return get(SecuritySupport.principal(a).userId(), a);
    }

    @GetMapping("/users/{id}")
    UserResponse get(@PathVariable Long id, Authentication a) {
        permit(id, a);
        return dto(find(id));
    }

    @PutMapping("/users/{id}")
    UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest x, Authentication a) {
        permit(id, a);
        User u = find(id);
        if (x.name() != null)
            u.setName(x.name());
        if (x.phone() != null)
            u.setPhone(x.phone());
        if (x.password() != null)
            u.setPassword(pe.encode(x.password()));
        return dto(r.save(u));
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, Authentication a) {
        if (!"ADMIN".equals(SecuritySupport.principal(a).role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        r.delete(find(id));
    }

    private User find(Long id) {
        return r.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void permit(Long id, Authentication a) {
        AuthPrincipal p = SecuritySupport.principal(a);
        if (!p.role().equals("ADMIN") && !p.userId().equals(id))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your profile");
    }

}
