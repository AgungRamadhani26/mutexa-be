package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.dto.request.LoginRequest;
import com.example.mutexa_be.dto.request.RegisterUserRequest;
import com.example.mutexa_be.dto.request.UpdateUserRequest;
import com.example.mutexa_be.dto.response.AuthResponse;
import com.example.mutexa_be.dto.response.UserResponse;
import com.example.mutexa_be.service.AuthService;
import com.example.mutexa_be.service.UserService;
import com.example.mutexa_be.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

   private final AuthService authService;
   private final UserService userService;

   /** Login — endpoint publik */
   @PostMapping("/login")
   public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
      AuthResponse response = authService.login(request);
      return ResponseUtil.ok(response, "Login berhasil. Selamat datang, " + response.getName() + "!");
   }

   /** Daftar semua user — hanya ADMIN */
   @GetMapping("/users")
   @PreAuthorize("hasRole('ADMIN')")
   public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
      return ResponseUtil.ok(userService.getAllUsers(), "Berhasil mengambil daftar user.");
   }

   /** Tambah user baru — hanya ADMIN */
   @PostMapping("/register")
   @PreAuthorize("hasRole('ADMIN')")
   public ResponseEntity<ApiResponse<UserResponse>> registerUser(@RequestBody RegisterUserRequest request) {
      UserResponse response = userService.registerUser(request);
      return ResponseUtil.created(response, "User " + response.getName() + " berhasil ditambahkan.");
   }

   /** Nonaktifkan user — hanya ADMIN */
   @PatchMapping("/users/{id}/deactivate")
   @PreAuthorize("hasRole('ADMIN')")
   public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable Long id) {
      return ResponseUtil.ok(userService.deactivateUser(id), "User berhasil dinonaktifkan.");
   }

   /** Aktifkan user — hanya ADMIN */
   @PatchMapping("/users/{id}/activate")
   @PreAuthorize("hasRole('ADMIN')")
   public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable Long id) {
      return ResponseUtil.ok(userService.activateUser(id), "User berhasil diaktifkan.");
   }

   /** Edit user — hanya ADMIN */
   @PutMapping("/users/{id}")
   @PreAuthorize("hasRole('ADMIN')")
   public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable Long id,
         @RequestBody UpdateUserRequest request) {
      return ResponseUtil.ok(userService.updateUser(id, request), "Data user berhasil diperbarui.");
   }

   /** Ganti password untuk user yang sedang login */
   @PostMapping("/change-password")
   public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody com.example.mutexa_be.dto.request.ChangePasswordRequest request) {
      String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
      userService.changePassword(email, request);
      return ResponseUtil.ok(null, "Password Anda berhasil diganti.");
   }
}
