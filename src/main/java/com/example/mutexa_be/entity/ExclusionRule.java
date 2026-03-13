package com.example.mutexa_be.entity;

import com.example.mutexa_be.entity.enums.RuleType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exclusion_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExclusionRule {

   // ID Unik untuk aturan pengecualian secara generik
   @Id
   @GeneratedValue(strategy = GenerationType.UUID)
   private UUID id;

   // Relasi terhadap rekening yang aturannya difokuskan (agar tidak tertukar dengan rekening lain)
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "account_id", nullable = false)
   private BankAccount bankAccount;

   // Tipe dari pengecualian apakah berbasis NAMA, NOMOR REKENING, atau KEYWORD pola tertentu
   @Enumerated(EnumType.STRING)
   @Column(name = "rule_type", nullable = false)
   private RuleType ruleType;

   // Keyword pencarian (contohnya bisa String spesifik "Potongan Pajak")
   @Column(name = "pattern", nullable = false)
   private String pattern;

   // Status apakah aturan ini diaktifkan untuk filtering dashboard sekarang
   @Column(name = "is_active", nullable = false)
   private Boolean isActive = true;

   // Tanggal & waktu spesifik aturan ini ditambahkan
   @CreationTimestamp
   @Column(name = "created_at", updatable = false)
   private LocalDateTime createdAt;

   // Tanggal update jika pattern/tipe atau status diperbarui
   @UpdateTimestamp
   @Column(name = "updated_at")
   private LocalDateTime updatedAt;
}