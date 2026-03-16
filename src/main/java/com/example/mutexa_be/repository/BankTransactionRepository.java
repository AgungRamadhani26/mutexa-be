package com.example.mutexa_be.repository;

import com.example.mutexa_be.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
   boolean existsByDuplicateHash(String duplicateHash);
}