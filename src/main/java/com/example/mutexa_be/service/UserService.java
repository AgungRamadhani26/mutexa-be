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

   private void validateEmail(String email) {
      if (email == null || email.trim().isEmpty()) {
         throw new IllegalArgumentException("Email tidak boleh kosong.");
      }
      String emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
      if (!email.matches(emailRegex)) {
         throw new IllegalArgumentException("Format email tidak valid (contoh: nama@email.com).");
      }
   }

   private void validatePassword(String password) {
      java.util.List<String> errors = new java.util.ArrayList<>();
      if (password == null || password.length() < 8) errors.add("minimal 8 karakter");
      if (password == null || !password.matches(".*[A-Z].*")) errors.add("huruf besar");
      if (password == null || !password.matches(".*[a-z].*")) errors.add("huruf kecil");
      if (password == null || !password.matches(".*\\d.*")) errors.add("angka");
      if (password == null || !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) errors.add("karakter khusus");
      
      if (!errors.isEmpty()) {
         throw new IllegalArgumentException("Password harus: " + String.join(", ", errors) + ".");
      }
   }

   public UserResponse registerUser(RegisterUserRequest request) {
      if (request.getName() == null || request.getName().trim().isEmpty()) {
         throw new IllegalArgumentException("Nama lengkap tidak boleh kosong.");
      }
      if (request.getRole() == null || request.getRole().trim().isEmpty()) {
         throw new IllegalArgumentException("Role tidak boleh kosong.");
      }
      
      validateEmail(request.getEmail());
      validatePassword(request.getPassword());

      if (userRepository.existsByEmail(request.getEmail())) {
         throw new IllegalArgumentException("Email sudah terdaftar: " + request.getEmail());
      }

      User user = User.builder()
            .name(request.getName().trim())
            .email(request.getEmail().trim())
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
            
      validatePassword(newPassword);
      
      user.setPassword(passwordEncoder.encode(newPassword));
      return UserResponse.fromEntity(userRepository.save(user));
   }
}
