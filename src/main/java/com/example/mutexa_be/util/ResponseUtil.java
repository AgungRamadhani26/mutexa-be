package com.example.mutexa_be.util;

import com.example.mutexa_be.base.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

public class ResponseUtil {

   private ResponseUtil() {
      // Private constructor to prevent instantiation
   }

   public static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
      ApiResponse<T> body = ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .code(HttpStatus.OK.value())
            .timestamp(Instant.now())
            .build();
      return ResponseEntity.ok(body);
   }

   public static <T> ResponseEntity<ApiResponse<T>> success(T data, String message, HttpStatus status) {
      ApiResponse<T> body = ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .code(status.value())
            .timestamp(Instant.now())
            .build();
      return ResponseEntity.status(status).body(body);
   }

   public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
      ApiResponse<T> body = ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .code(HttpStatus.CREATED.value())
            .timestamp(Instant.now())
            .build();
      return ResponseEntity.status(HttpStatus.CREATED).body(body);
   }

   public static ResponseEntity<ApiResponse<Object>> error(HttpStatus status, String message) {
      ApiResponse<Object> body = ApiResponse.builder()
            .success(false)
            .message(message)
            .data(null)
            .code(status.value())
            .timestamp(Instant.now())
            .build();
      return ResponseEntity.status(status).body(body);
   }

   public static ResponseEntity<ApiResponse<Object>> error(HttpStatus status, String message, Object data) {
      ApiResponse<Object> body = ApiResponse.builder()
            .success(false)
            .message(message)
            .data(data)
            .code(status.value())
            .timestamp(Instant.now())
            .build();
      return ResponseEntity.status(status).body(body);
   }
}