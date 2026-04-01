package com.example.mutexa_be.repository;

import com.example.mutexa_be.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
   Optional<BankAccount> findByAccountNumber(String accountNumber);

   @Query("SELECT new com.example.mutexa_be.dto.response.AccountWithDocumentsResponse(" +
          "b.id, b.accountNumber, b.accountName, b.bankName, COUNT(d.id)) " +
          "FROM BankAccount b LEFT JOIN MutationDocument d ON d.bankAccount.id = b.id " +
          "GROUP BY b.id, b.accountNumber, b.accountName, b.bankName " +
          "ORDER BY b.accountName ASC")
   List<com.example.mutexa_be.dto.response.AccountWithDocumentsResponse> getAccountsWithDocumentCount();
}