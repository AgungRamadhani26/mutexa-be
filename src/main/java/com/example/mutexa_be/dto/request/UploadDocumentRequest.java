package com.example.mutexa_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadDocumentRequest {

   // File yang diupload
   private MultipartFile file;

   // Nama bank dari rekening ini, misalnya "BCA" atau "BRI"
   @NotBlank(message = "Nama Bank tidak boleh kosong")
   private String bankName;

   // Nomor rekening, akan dicari di database atau dibuatkan jika belum ada
   @NotBlank(message = "Nomor Rekening tidak boleh kosong")
   private String accountNumber;

   // Nama pemilik rekening
   @NotBlank(message = "Nama Pemilik Rekening tidak boleh kosong")
   private String accountName;
}