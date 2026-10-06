package com.foodordering.notification;

import com.foodordering.security.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.*;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;

@Configuration @EnableWebSecurity @EnableMethodSecurity
class NotificationSecurity {@Bean SecurityFilterChain chain(HttpSecurity h,JwtAuthenticationFilter f,JsonSecurityHandlers x)throws Exception{return h.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/actuator/**").permitAll().requestMatchers("/notifications/**").hasRole("ADMIN").anyRequest().authenticated()).exceptionHandling(e->e.authenticationEntryPoint(x).accessDeniedHandler(x)).addFilterBefore(f,org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class).build();}}
