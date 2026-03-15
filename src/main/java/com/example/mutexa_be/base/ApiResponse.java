package com.example.mutexa_be.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
   private Boolean success;
   private String message;
   private T data;
   private Integer code;
   @Builder.Default
   private Instant timestamp = Instant.now();
}