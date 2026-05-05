package com.example.mutexa_be.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

   @Value("${jwt.secret}")
   private String secret;

   @Value("${jwt.expiration-ms}")
   private long expirationMs;

   private SecretKey getSigningKey() {
      return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
   }

   // Generate token dari email dan role user
   public String generateToken(String email, String role) {
      return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey())
            .compact();
   }

   // Ekstrak semua claims dari token
   public Claims extractClaims(String token) {
      return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
   }

   public String extractEmail(String token) {
      return extractClaims(token).getSubject();
   }

   public String extractRole(String token) {
      return extractClaims(token).get("role", String.class);
   }

   public boolean isTokenValid(String token) {
      try {
         return extractClaims(token).getExpiration().after(new Date());
      } catch (Exception e) {
         return false;
      }
   }
}
