package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.dto.request.UploadDocumentRequest;
import com.example.mutexa_be.dto.response.DocumentUploadResponse;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.service.DocumentService;
import com.example.mutexa_be.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

   private final DocumentService documentService;

   @PostMapping(value = "/upload", consumes = "multipart/form-data")
   public ResponseEntity<ApiResponse<DocumentUploadResponse>> uploadDocument(
         @Valid @ModelAttribute UploadDocumentRequest request) {

      // Validasi ekstensi dasar
      String originalFilename = request.getFile().getOriginalFilename();
      if (originalFilename == null ||
            (!originalFilename.toLowerCase().endsWith(".pdf") &&
                  !originalFilename.toLowerCase().endsWith(".png") &&
                  !originalFilename.toLowerCase().endsWith(".jpg") &&
                  !originalFilename.toLowerCase().endsWith(".jpeg"))) {
         throw new IllegalArgumentException("Format file harus PDF, PNG, atau JPG.");
      }

      MutationDocument savedDoc = documentService.uploadAndRegisterDocument(request);
      DocumentUploadResponse responseDto = DocumentUploadResponse.fromEntity(savedDoc);

      return ResponseUtil.created(responseDto,
            "Dokumen " + responseDto.getFileName() + " berhasil diupload dan sedang diproses.");
   }
}