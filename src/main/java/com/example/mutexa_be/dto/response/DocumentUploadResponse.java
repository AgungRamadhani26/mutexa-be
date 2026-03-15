package com.example.mutexa_be.dto.response;

import com.example.mutexa_be.entity.MutationDocument;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DocumentUploadResponse {
   private UUID documentId;
   private String fileName;
   private String status;
   private String fileType;
   private String accountName;
   private String accountNumber;

   // Konversi sederhana dari Entity ke DTO
   public static DocumentUploadResponse fromEntity(MutationDocument entity) {
      return DocumentUploadResponse.builder()
            .documentId(entity.getId())
            .fileName(entity.getFileName())
            .status(entity.getStatus().name())
            .fileType(entity.getFileType().name())
            .accountName(entity.getBankAccount().getAccountName())
            .accountNumber(entity.getBankAccount().getAccountNumber())
            .build();
   }
}