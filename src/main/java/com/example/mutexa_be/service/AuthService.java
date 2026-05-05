package com.example.mutexa_be.service;

import com.example.mutexa_be.dto.request.LoginRequest;
import com.example.mutexa_be.dto.response.AuthResponse;
import com.example.mutexa_be.entity.User;
import com.example.mutexa_be.repository.UserRepository;
import com.example.mutexa_be.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

   private final UserRepository userRepository;
   private final JwtUtil jwtUtil;
   private final BCryptPasswordEncoder passwordEncoder;

   public AuthResponse login(LoginRequest request) {
      User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new IllegalArgumentException("Email atau password salah."));

      if (!user.getIsActive()) {
         throw new IllegalArgumentException("Akun Anda telah dinonaktifkan. Hubungi administrator.");
      }

      if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
         throw new IllegalArgumentException("Email atau password salah.");
      }

      String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

      return AuthResponse.builder()
            .token(token)
            .name(user.getName())
            .email(user.getEmail())
            .role(user.getRole().name())
            .build();
   }
}
