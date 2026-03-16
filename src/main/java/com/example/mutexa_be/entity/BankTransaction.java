package com.example.mutexa_be.entity;

import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bank_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransaction {

   // ID Unik setiap baris transaksi yang tercatat
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   // Relasi ke dokumen mutasi yang merupakan sumber dari line item transaksi ini
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "document_id", nullable = false)
   private MutationDocument mutationDocument;

   // Relasi berwujud rekening utama dari mana transaksi ini terjadi
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "account_id", nullable = false)
   private BankAccount bankAccount;

   // Tanggal terjadinya transaksi pada rekening
   @Column(name = "transaction_date", nullable = false)
   private LocalDate transactionDate;

   // Deskripsi asli transaksi langsung dari hasil bacaan file/OCR (tidak difilter)
   @Column(name = "raw_description", columnDefinition = "TEXT")
   private String rawDescription;

   // Deskripsi yang dibersihkan/normalisasi oleh sistem untuk dibaca user
   @Column(name = "normalized_description", columnDefinition = "TEXT")
   private String normalizedDescription;

   // Tipe mutasi (CR untuk Kredit / Uang masuk, DB untuk Debit / Uang keluar)
   @Enumerated(EnumType.STRING)
   @Column(name = "mutation_type", nullable = false)
   private MutationType mutationType;

   // Nilai atau jumlah uang yang dimutasikan (skala sampai 4 digit desimal)
   @Column(name = "amount", nullable = false, precision = 19, scale = 4)
   private BigDecimal amount;

   // Sisa saldo setelah mutasi tersebut (jika berhasil diekstrak)
   @Column(name = "balance", precision = 19, scale = 4)
   private BigDecimal balance;

   // Kategori analisis dari transaksi (misalnya INCOME, TAX, ADMIN, dll)
   @Enumerated(EnumType.STRING)
   @Column(name = "category", nullable = false)
   private TransactionCategory category;

   // Bendera apakah transaksi ini dikecualikan (excluded) pada dashboard atau
   // tidak
   @Column(name = "is_excluded", nullable = false)
   private Boolean isExcluded = false;

   // Kode hash yang tersusun dari (Tanggal+Nilai+Tipe+Desktripsi) untuk mencegah
   // duplikasi simpan
   @Column(name = "duplicate_hash", unique = true, nullable = false)
   private String duplicateHash;

   // Waktu rekam transaksi ini ke dalam database mutasi internal
   @CreationTimestamp
   @Column(name = "created_at", updatable = false)
   private LocalDateTime createdAt;
}