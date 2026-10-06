package com.foodordering.security;
import org.springframework.security.core.Authentication;
public final class SecuritySupport { private SecuritySupport(){} public static AuthPrincipal principal(Authentication a){return (AuthPrincipal)a.getPrincipal();} }
