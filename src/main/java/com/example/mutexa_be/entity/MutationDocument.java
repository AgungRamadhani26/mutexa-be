package com.example.mutexa_be.entity;

import com.example.mutexa_be.entity.enums.DocumentStatus;
import com.example.mutexa_be.entity.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mutation_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MutationDocument {

   // ID Unik dokumen mutasi, di-generate secara otomatis
   @Id
   @GeneratedValue(strategy = GenerationType.UUID)
   private UUID id;

   // Relasi Many-To-One terhadap rekening yang dimiliki dokumen ini
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "account_id", nullable = false)
   private BankAccount bankAccount;

   // Nama file yang diunggah oleh pengguna (contoh: "Mutasi_BCA_Januari.pdf")
   @Column(name = "file_name", nullable = false)
   private String fileName;

   // Tipe dokumen yang diunggah berupa enum (PDF_DIGITAL atau IMAGE_SCAN)
   @Enumerated(EnumType.STRING)
   @Column(name = "file_type", nullable = false)
   private DocumentType fileType;

   // Status pemrosesan dari dokumen (UPLOADED, PARSING, NORMALIZING, SUCCESS,
   // dsb.)
   @Enumerated(EnumType.STRING)
   @Column(name = "status", nullable = false)
   private DocumentStatus status;

   // Tanggal awal dari periode waktu transaksi mutasi
   @Column(name = "period_start")
   private LocalDate periodStart;

   // Tanggal akhir dari periode waktu transaksi mutasi
   @Column(name = "period_end")
   private LocalDate periodEnd;

   // Tempat menyimpan pesan error jika terjadi masalah pada saat parsing OCR/PDF
   @Column(name = "error_message", columnDefinition = "TEXT")
   private String errorMessage;

   // Path relatif atau absolute pada server dimana file disimpan
   @Column(name = "file_path", nullable = false)
   private String filePath;

   // Tanggal & waktu dokumen ini tercatat di-upload ke sistem
   @CreationTimestamp
   @Column(name = "created_at", updatable = false)
   private LocalDateTime createdAt;

   // Tanggal & waktu update track status dokumen terkini
   @UpdateTimestamp
   @Column(name = "updated_at")
   private LocalDateTime updatedAt;
}