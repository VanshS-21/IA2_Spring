package com.foodordering.security;
import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtUtil jwt; public JwtAuthenticationFilter(JwtUtil jwt){this.jwt=jwt;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {String h=req.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer "))try{AuthPrincipal p=jwt.principal(h.substring(7)); var a=new UsernamePasswordAuthenticationToken(p,null,java.util.List.of(new SimpleGrantedAuthority("ROLE_"+p.role()))); SecurityContextHolder.getContext().setAuthentication(a);}catch(Exception ignored){} chain.doFilter(req,res);}
}
