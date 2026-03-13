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

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private MutationDocument mutationDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount bankAccount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "raw_description", columnDefinition = "TEXT")
    private String rawDescription;

    @Column(name = "normalized_description", columnDefinition = "TEXT")
    private String normalizedDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "mutation_type", nullable = false)
    private MutationType mutationType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance", precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private TransactionCategory category;

    @Column(name = "is_excluded", nullable = false)
    private Boolean isExcluded = false;

    @Column(name = "duplicate_hash", unique = true, nullable = false)
    private String duplicateHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}