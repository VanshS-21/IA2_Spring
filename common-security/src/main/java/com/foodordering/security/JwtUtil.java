package com.foodordering.security;
import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys; import java.nio.charset.StandardCharsets; import java.util.*; import javax.crypto.SecretKey; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component;
@Component public class JwtUtil {
 private final SecretKey key; private final long expiration;
 public JwtUtil(@Value("${jwt.secret}") String secret,@Value("${jwt.expiration-ms}") long expiration){this.key=Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));this.expiration=expiration;}
 public String generate(String email,Long userId,String role){Date now=new Date();return Jwts.builder().subject(email).claim("userId",userId).claim("role",role).issuedAt(now).expiration(new Date(now.getTime()+expiration)).signWith(key).compact();}
 public Claims claims(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
 public AuthPrincipal principal(String token){Claims c=claims(token);return new AuthPrincipal(c.get("userId",Long.class),c.getSubject(),c.get("role",String.class));}
 public long expiration(){return expiration;}
}
