package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.dto.request.UploadDocumentRequest;
import com.example.mutexa_be.dto.response.DocumentUploadResponse;
import com.example.mutexa_be.dto.response.AccountWithDocumentsResponse;
import com.example.mutexa_be.dto.response.DocumentListResponse;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.service.DocumentService;
import com.example.mutexa_be.util.ResponseUtil;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

   private final DocumentService documentService;

   private String getCurrentUserEmail() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
         return auth.getName();
      }
      return null;
   }

   private boolean isAdmin() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null) {
         return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
      }
      return false;
   }

   /**
    * Endpoint Level 1: Mengambil daftar Rekening Bank.
    * Tampilan awal (Level 1) di Frontend membutuhkan daftar rekening beserta
    * total jumlah dokumen yang telah diupload untuk masing-masing rekening.
    */
   @GetMapping("/by-account")
   public ResponseEntity<ApiResponse<List<AccountWithDocumentsResponse>>> getAccounts() {
       String email = getCurrentUserEmail();
       boolean admin = isAdmin();
       List<AccountWithDocumentsResponse> accounts = documentService.getAccountsWithDocumentCount(email, admin);
       return ResponseUtil.ok(accounts, "Berhasil mengambil daftar rekening bank");
   }

   /**
    * Endpoint Level 2: Mengambil daftar Dokumen Mutasi per Rekening.
    * Ketika user mengklik nama rekening, Frontend memanggil endpoint ini
    * untuk menampilkan daftar riwayat upload (SUCCESS/FAILED) miliknya.
    */
   @GetMapping("/by-account/{accountId}")
   public ResponseEntity<ApiResponse<List<DocumentListResponse>>> getDocumentsByAccount(@PathVariable Long accountId) {
       String email = getCurrentUserEmail();
       boolean admin = isAdmin();
       List<DocumentListResponse> documents = documentService.getDocumentsByAccountId(accountId, email, admin);
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

      String email = getCurrentUserEmail();
      boolean admin = isAdmin();
      MutationDocument savedDoc = documentService.uploadAndRegisterDocument(request, email, admin);
      DocumentUploadResponse responseDto = DocumentUploadResponse.fromEntity(savedDoc);

      return ResponseUtil.created(responseDto,
            "Dokumen " + responseDto.getFileName() + " berhasil diupload dan sedang diproses.");
   }

   /**
    * Endpoint hapus rekening bank — hanya ADMIN.
    * Menghapus rekening beserta semua dokumen & transaksi yang terkait (cascade).
    */
   @Transactional
   @DeleteMapping("/account/{accountId}")
   @PreAuthorize("hasRole('ADMIN')")
   public ResponseEntity<ApiResponse<String>> deleteAccount(@PathVariable Long accountId) {
      documentService.deleteAccount(accountId);
      return ResponseUtil.ok("Rekening ID " + accountId + " berhasil dihapus.",
            "Rekening dan seluruh data terkaitnya telah dihapus secara permanen.");
   }
}