package com.example.mutexa_be.service;

import com.example.mutexa_be.dto.request.RegisterUserRequest;
import com.example.mutexa_be.dto.response.UserResponse;
import com.example.mutexa_be.entity.User;
import com.example.mutexa_be.entity.enums.UserRole;
import com.example.mutexa_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

   private final UserRepository userRepository;
   private final BCryptPasswordEncoder passwordEncoder;

   public List<UserResponse> getAllUsers() {
      return userRepository.findAll()
            .stream()
            .map(UserResponse::fromEntity)
            .toList();
   }

   public UserResponse registerUser(RegisterUserRequest request) {
      if (userRepository.existsByEmail(request.getEmail())) {
         throw new IllegalArgumentException("Email sudah terdaftar: " + request.getEmail());
      }

      User user = User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(UserRole.valueOf(request.getRole()))
            .isActive(true)
            .build();

      return UserResponse.fromEntity(userRepository.save(user));
   }

   public UserResponse deactivateUser(Long userId) {
      User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan: " + userId));
      user.setIsActive(false);
      return UserResponse.fromEntity(userRepository.save(user));
   }

   public UserResponse activateUser(Long userId) {
      User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan: " + userId));
      user.setIsActive(true);
      return UserResponse.fromEntity(userRepository.save(user));
   }

   public UserResponse resetPassword(Long userId, String newPassword) {
      User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan: " + userId));
      user.setPassword(passwordEncoder.encode(newPassword));
      return UserResponse.fromEntity(userRepository.save(user));
   }
}
