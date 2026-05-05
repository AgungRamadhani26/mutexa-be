package com.example.mutexa_be.dto.request;

import lombok.Data;

@Data
public class RegisterUserRequest {
   private String name;
   private String email;
   private String password;
   private String role; // "ANALYST" atau "ADMIN"
}
