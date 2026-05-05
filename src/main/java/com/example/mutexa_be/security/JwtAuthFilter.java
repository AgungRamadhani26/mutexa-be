package com.example.mutexa_be.security;

import com.example.mutexa_be.entity.User;
import com.example.mutexa_be.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

   private final JwtUtil jwtUtil;
   private final UserRepository userRepository;

   @Override
   protected void doFilterInternal(HttpServletRequest request,
         HttpServletResponse response,
         FilterChain filterChain) throws ServletException, IOException {

      String authHeader = request.getHeader("Authorization");

      if (authHeader == null || !authHeader.startsWith("Bearer ")) {
         filterChain.doFilter(request, response);
         return;
      }

      String token = authHeader.substring(7);

      if (!jwtUtil.isTokenValid(token)) {
         filterChain.doFilter(request, response);
         return;
      }

      String email = jwtUtil.extractEmail(token);
      String role = jwtUtil.extractRole(token);

      Optional<User> userOpt = userRepository.findByEmail(email);
      if (userOpt.isEmpty() || !userOpt.get().getIsActive()) {
         filterChain.doFilter(request, response);
         return;
      }

      // Set authentication ke SecurityContext
      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

      SecurityContextHolder.getContext().setAuthentication(auth);
      filterChain.doFilter(request, response);
   }
}
