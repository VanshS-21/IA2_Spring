package com.foodordering.security;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class JwtUtilTest {
    private static final String SECRET = "VGhpc0lzQVN1ZmZpY2llbnRseUxvbmdCYXNlNjRFbmNvZGVkU2VjcmV0S2V5Rm9ySFMyNTY=";
    @Test void tokenPreservesAuthenticatedIdentityAndRole() {
        JwtUtil jwt = new JwtUtil(SECRET, 60_000);
        AuthPrincipal principal = jwt.principal(jwt.generate("a@food.com", 42L, "CUSTOMER"));
        assertEquals(42L, principal.userId());
        assertEquals("CUSTOMER", principal.role());
        assertEquals("a@food.com", principal.email());
    }
}
