package com.example.mutexa_be.exception;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

   @ExceptionHandler(IllegalArgumentException.class)
   public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
      log.warn("IllegalArgumentException: {}", ex.getMessage());
      return ResponseUtil.error(HttpStatus.BAD_REQUEST, ex.getMessage());
   }

   @ExceptionHandler(MaxUploadSizeExceededException.class)
   public ResponseEntity<ApiResponse<Object>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
      log.warn("MaxUploadSizeExceededException: {}", exc.getMessage());
      return ResponseUtil.error(HttpStatus.PAYLOAD_TOO_LARGE, "File terlalu besar! Maksimal ukuran file adalah 10MB.");
   }

   @ExceptionHandler(Exception.class)
   public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
      log.error("Unhandled exception occurred", ex);
      return ResponseUtil.error(HttpStatus.INTERNAL_SERVER_ERROR,
            "Terjadi kesalahan pada server. Silakan coba lagi nanti.");
   }
}