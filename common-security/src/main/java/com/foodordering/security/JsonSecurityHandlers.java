package com.foodordering.security;
import jakarta.servlet.http.*; import java.io.IOException; import java.time.Instant; import org.springframework.http.*; import org.springframework.security.access.AccessDeniedException; import org.springframework.security.web.AuthenticationEntryPoint; import org.springframework.security.web.access.AccessDeniedHandler; import org.springframework.stereotype.Component;
@Component public class JsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
 private void out(HttpServletResponse r,int status,String error,String message,String path)throws IOException{r.setStatus(status);r.setContentType(MediaType.APPLICATION_JSON_VALUE);r.getWriter().printf("{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",Instant.now(),status,error,message,path);}
 public void commence(HttpServletRequest q,HttpServletResponse r,org.springframework.security.core.AuthenticationException e)throws IOException{out(r,401,"Unauthorized","Authentication is required",q.getRequestURI());}
 public void handle(HttpServletRequest q,HttpServletResponse r,AccessDeniedException e)throws IOException{out(r,403,"Forbidden","You do not have permission for this resource",q.getRequestURI());}
}
