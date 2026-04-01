package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.dto.request.UploadDocumentRequest;
import com.example.mutexa_be.dto.response.DocumentUploadResponse;
import com.example.mutexa_be.dto.response.AccountWithDocumentsResponse;
import com.example.mutexa_be.dto.response.DocumentListResponse;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.service.DocumentService;
import com.example.mutexa_be.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

   private final DocumentService documentService;

   /**
    * Endpoint Level 1: Mengambil daftar Rekening Bank.
    * Tampilan awal (Level 1) di Frontend membutuhkan daftar rekening beserta
    * total jumlah dokumen yang telah diupload untuk masing-masing rekening.
    */
   @GetMapping("/by-account")
   public ResponseEntity<ApiResponse<List<AccountWithDocumentsResponse>>> getAccounts() {
       List<AccountWithDocumentsResponse> accounts = documentService.getAccountsWithDocumentCount();
       return ResponseUtil.ok(accounts, "Berhasil mengambil daftar rekening bank");
   }

   /**
    * Endpoint Level 2: Mengambil daftar Dokumen Mutasi per Rekening.
    * Ketika user mengklik nama rekening, Frontend memanggil endpoint ini
    * untuk menampilkan daftar riwayat upload (SUCCESS/FAILED) miliknya.
    */
   @GetMapping("/by-account/{accountId}")
   public ResponseEntity<ApiResponse<List<DocumentListResponse>>> getDocumentsByAccount(@PathVariable Long accountId) {
       List<DocumentListResponse> documents = documentService.getDocumentsByAccountId(accountId);
       return ResponseUtil.ok(documents, "Berhasil mengambil daftar dokumen");
   }

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