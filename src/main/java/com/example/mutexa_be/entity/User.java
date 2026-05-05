package com.example.mutexa_be.entity;

import com.example.mutexa_be.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "name", nullable = false)
   private String name;

   // Email digunakan sebagai username untuk login
   @Column(name = "email", nullable = false, unique = true)
   private String email;

   // Password yang disimpan harus dalam bentuk BCrypt hash
   @Column(name = "password", nullable = false)
   private String password;

   @Enumerated(EnumType.STRING)
   @Column(name = "role", nullable = false)
   private UserRole role;

   // Nonaktifkan user tanpa menghapus datanya (soft disable)
   @Column(name = "is_active", nullable = false)
   @Builder.Default
   private Boolean isActive = true;

   @CreationTimestamp
   @Column(name = "created_at", updatable = false)
   private LocalDateTime createdAt;

   @UpdateTimestamp
   @Column(name = "updated_at")
   private LocalDateTime updatedAt;
}
