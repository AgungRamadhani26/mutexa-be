package com.example.mutexa_be.dto.response;

import com.example.mutexa_be.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
   private Long id;
   private String name;
   private String email;
   private String role;
   private Boolean isActive;
   private LocalDateTime createdAt;

   public static UserResponse fromEntity(User user) {
      return UserResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .role(user.getRole().name())
            .isActive(user.getIsActive())
            .createdAt(user.getCreatedAt())
            .build();
   }
}
