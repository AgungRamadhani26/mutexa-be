package com.example.mutexa_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "bank_account", uniqueConstraints = {
      @UniqueConstraint(columnNames = {"account_number", "bank_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {

   // ID unik utama untuk rekening bank
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   // Nomor rekening yang wajib diisi (kombinasi dengan bank_name harus unik)
   @Column(name = "account_number", nullable = false)
   private String accountNumber;

   // Nama lengkap nasabah / pemilik rekening
   @Column(name = "account_name", nullable = false)
   private String accountName;

   // Nama dari bank yang menerbitkan rekening (Misal: BCA, Mandiri)
   @Column(name = "bank_name", nullable = false)
   private String bankName;

   // Cascade delete: jika BankAccount dihapus, semua dokumennya ikut terhapus
   @OneToMany(mappedBy = "bankAccount", cascade = CascadeType.ALL, orphanRemoval = true)
   private List<MutationDocument> documents;

   // Tanggal & waktu pencatatan data ke tabel secara otomatis (hanya saat insert)
   @CreationTimestamp
   @Column(name = "created_at", updatable = false)
   private LocalDateTime createdAt;

   // Tanggal & waktu terakhir data diubah yang dicatat otomatis (terjadi saat
   // update)
   @UpdateTimestamp
   @Column(name = "updated_at")
   private LocalDateTime updatedAt;
}