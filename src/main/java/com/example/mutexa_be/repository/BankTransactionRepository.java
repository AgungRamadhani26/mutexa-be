package com.example.mutexa_be.repository;

import com.example.mutexa_be.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
   boolean existsByDuplicateHash(String duplicateHash);

   @Query(value = "SELECT " +
         "    YEAR(t.transaction_date) AS year, " +
         "    MONTH(t.transaction_date) AS month, " +
         "    SUM(CASE WHEN t.mutation_type = 'CR' THEN t.amount ELSE 0 END) AS totalCredit, " +
         "    SUM(CASE WHEN t.mutation_type = 'DB' THEN t.amount ELSE 0 END) AS totalDebit, " +
         "    SUM(CASE WHEN t.mutation_type = 'CR' THEN 1 ELSE 0 END) AS freqCredit, " +
         "    SUM(CASE WHEN t.mutation_type = 'DB' THEN 1 ELSE 0 END) AS freqDebit, " +
         "    (SELECT TOP 1 b.balance FROM bank_transaction b " +
         "     WHERE YEAR(b.transaction_date) = YEAR(t.transaction_date) " +
         "     AND MONTH(b.transaction_date) = MONTH(t.transaction_date) " +
         "     ORDER BY b.transaction_date DESC, b.id DESC) AS saldoAkhir " +
         "FROM bank_transaction t " +
         "GROUP BY YEAR(t.transaction_date), MONTH(t.transaction_date) " +
         "ORDER BY YEAR(t.transaction_date) DESC, MONTH(t.transaction_date) DESC", nativeQuery = true)
   List<Object[]> getMonthlySummary();

   // Get Monthly Summary filtered by Document ID
   @Query(value = "SELECT " +
         "    YEAR(t.transaction_date) AS year, " +
         "    MONTH(t.transaction_date) AS month, " +
         "    SUM(CASE WHEN t.mutation_type = 'CR' THEN t.amount ELSE 0 END) AS totalCredit, " +
         "    SUM(CASE WHEN t.mutation_type = 'DB' THEN t.amount ELSE 0 END) AS totalDebit, " +
         "    SUM(CASE WHEN t.mutation_type = 'CR' THEN 1 ELSE 0 END) AS freqCredit, " +
         "    SUM(CASE WHEN t.mutation_type = 'DB' THEN 1 ELSE 0 END) AS freqDebit, " +
         "    (SELECT TOP 1 b.balance FROM bank_transaction b " +
         "     WHERE YEAR(b.transaction_date) = YEAR(t.transaction_date) " +
         "     AND MONTH(b.transaction_date) = MONTH(t.transaction_date) " +
         "     AND b.document_id = :documentId " +
         "     ORDER BY b.transaction_date DESC, b.id DESC) AS saldoAkhir " +
         "FROM bank_transaction t " +
         "WHERE t.document_id = :documentId " +
         "GROUP BY YEAR(t.transaction_date), MONTH(t.transaction_date) " +
         "ORDER BY YEAR(t.transaction_date) DESC, MONTH(t.transaction_date) DESC", nativeQuery = true)
   List<Object[]> getMonthlySummaryByDocumentId(Long documentId);

   // Get ALL filtered by Document ID
   List<BankTransaction> findAllByMutationDocumentIdOrderByTransactionDateAscIdAsc(Long mutationDocumentId);
}