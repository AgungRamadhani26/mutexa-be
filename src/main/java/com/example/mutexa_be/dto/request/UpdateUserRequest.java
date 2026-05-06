package com.example.mutexa_be.dto.request;

import lombok.Data;

@Data
public class UpdateUserRequest {
   private String name;
   private String email;
   private String password; // optional
   private String role;
}
