package com.example.mutexa_be.repository;

import com.example.mutexa_be.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
      boolean existsByDuplicateHash(String duplicateHash);

      @Query(value = "WITH MonthlyStats AS ( " +
                  "    SELECT " +
                  "        YEAR(t.transaction_date) AS year, " +
                  "        MONTH(t.transaction_date) AS month, " +
                  "        SUM(CASE WHEN t.mutation_type = 'CR' THEN t.amount ELSE 0 END) AS totalCredit, " +
                  "        SUM(CASE WHEN t.mutation_type = 'DB' THEN t.amount ELSE 0 END) AS totalDebit, " +
                  "        SUM(CASE WHEN t.mutation_type = 'CR' THEN 1 ELSE 0 END) AS freqCredit, " +
                  "        SUM(CASE WHEN t.mutation_type = 'DB' THEN 1 ELSE 0 END) AS freqDebit " +
                  "    FROM bank_transaction t " +
                  "    GROUP BY YEAR(t.transaction_date), MONTH(t.transaction_date) " +
                  ") " +
                  "SELECT " +
                  "    m.year, " +
                  "    m.month, " +
                  "    m.totalCredit, " +
                  "    m.totalDebit, " +
                  "    m.freqCredit, " +
                  "    m.freqDebit, " +
                  "    (SELECT TOP 1 b.balance FROM bank_transaction b " +
                  "     WHERE YEAR(b.transaction_date) = m.year " +
                  "     AND MONTH(b.transaction_date) = m.month " +
                  "     ORDER BY b.transaction_date DESC, b.id DESC) AS saldoAkhir " +
                  "FROM MonthlyStats m " +
                  "ORDER BY m.year ASC, m.month ASC", nativeQuery = true)
      List<Object[]> getMonthlySummary();

      // Get Monthly Summary filtered by Document ID
      @Query(value = "WITH MonthlyStats AS ( " +
                  "    SELECT " +
                  "        YEAR(t.transaction_date) AS year, " +
                  "        MONTH(t.transaction_date) AS month, " +
                  "        SUM(CASE WHEN t.mutation_type = 'CR' THEN t.amount ELSE 0 END) AS totalCredit, " +
                  "        SUM(CASE WHEN t.mutation_type = 'DB' THEN t.amount ELSE 0 END) AS totalDebit, " +
                  "        SUM(CASE WHEN t.mutation_type = 'CR' THEN 1 ELSE 0 END) AS freqCredit, " +
                  "        SUM(CASE WHEN t.mutation_type = 'DB' THEN 1 ELSE 0 END) AS freqDebit, " +
                  "        SUM(CASE WHEN t.mutation_type = 'CR' AND (t.is_excluded = 0 OR t.is_excluded IS NULL) THEN t.amount ELSE 0 END) AS cleanedTotalCredit, "
                  +
                  "        SUM(CASE WHEN t.mutation_type = 'DB' AND (t.is_excluded = 0 OR t.is_excluded IS NULL) THEN t.amount ELSE 0 END) AS cleanedTotalDebit, "
                  +
                  "        SUM(CASE WHEN t.mutation_type = 'CR' AND (t.is_excluded = 0 OR t.is_excluded IS NULL) THEN 1 ELSE 0 END) AS cleanedFreqCredit, "
                  +
                  "        SUM(CASE WHEN t.mutation_type = 'DB' AND (t.is_excluded = 0 OR t.is_excluded IS NULL) THEN 1 ELSE 0 END) AS cleanedFreqDebit "
                  +
                  "    FROM bank_transaction t " +
                  "    WHERE t.document_id = :documentId " +
                  "    GROUP BY YEAR(t.transaction_date), MONTH(t.transaction_date) " +
                  ") " +
                  "SELECT " +
                  "    m.year, " +
                  "    m.month, " +
                  "    m.totalCredit, " +
                  "    m.totalDebit, " +
                  "    m.freqCredit, " +
                  "    m.freqDebit, " +
                  "    (SELECT TOP 1 b.balance FROM bank_transaction b " +
                  "     WHERE YEAR(b.transaction_date) = m.year " +
                  "     AND MONTH(b.transaction_date) = m.month " +
                  "     AND b.document_id = :documentId " +
                  "     ORDER BY b.transaction_date DESC, b.id DESC) AS saldoAkhir, " +
                  "    m.cleanedTotalCredit, " +
                  "    m.cleanedTotalDebit, " +
                  "    m.cleanedFreqCredit, " +
                  "    m.cleanedFreqDebit " +
                  "FROM MonthlyStats m " +
                  "ORDER BY m.year ASC, m.month ASC", nativeQuery = true)
      List<Object[]> getMonthlySummaryByDocumentId(Long documentId);

      // Get ALL filtered by Document ID
      List<BankTransaction> findAllByMutationDocumentIdOrderByTransactionDateAscIdAsc(Long mutationDocumentId);

      // Get ALL filtered by Document ID and Category (for Admin Table, etc.)
      List<BankTransaction> findAllByMutationDocumentIdAndCategoryOrderByTransactionDateAscIdAsc(
                  Long mutationDocumentId, com.example.mutexa_be.entity.enums.TransactionCategory category);

      // Get Top 10 Credit Amount by Document ID
      List<BankTransaction> findTop10ByMutationDocumentIdAndMutationTypeOrderByAmountDesc(Long mutationDocumentId,
                  com.example.mutexa_be.entity.enums.MutationType mutationType);

      // Menghitung jumlah perulangan (frekuensi) terbanyak dari suatu keterangan
      // mutasi (Credit)
      // Mengelompokkan MURNI berdasarkan nama pengirim/penerima (counterparty_name)
      // agar perhitungan frekuensi tidak bocor.
      @Query(value = "SELECT TOP 10 " +
                  "    CAST(COALESCE(t.counterparty_name, t.normalized_description, t.raw_description) AS VARCHAR(MAX)) AS keterangan, "
                  +
                  "    COUNT(t.id) AS frekuensi " +
                  "FROM bank_transaction t " +
                  "WHERE t.document_id = :documentId AND t.mutation_type = 'CR' " +
                  "GROUP BY CAST(COALESCE(t.counterparty_name, t.normalized_description, t.raw_description) AS VARCHAR(MAX)) "
                  +
                  "HAVING COUNT(t.id) >= 2 " +
                  "ORDER BY COUNT(t.id) DESC", nativeQuery = true)
      List<Object[]> findTop10CreditFreqByDocumentId(Long documentId);

      // Menghitung jumlah perulangan (frekuensi) terbanyak dari suatu keterangan
      // mutasi (Debit)
      @Query(value = "SELECT TOP 10 " +
                  "    CAST(COALESCE(t.counterparty_name, t.normalized_description, t.raw_description) AS VARCHAR(MAX)) AS keterangan, "
                  +
                  "    COUNT(t.id) AS frekuensi " +
                  "FROM bank_transaction t " +
                  "WHERE t.document_id = :documentId AND t.mutation_type = 'DB' " +
                  "GROUP BY CAST(COALESCE(t.counterparty_name, t.normalized_description, t.raw_description) AS VARCHAR(MAX)) "
                  +
                  "HAVING COUNT(t.id) >= 2 " +
                  "ORDER BY COUNT(t.id) DESC", nativeQuery = true)
      List<Object[]> findTop10DebitFreqByDocumentId(Long documentId);

      // Ringkasan Saldo & Arus Kas: total dan rata-rata credit/debit, exclude-aware
      @Query(value = "SELECT " +
                  "    SUM(CASE WHEN t.mutation_type = 'CR' THEN t.amount ELSE 0 END) AS totalCredit, " +
                  "    SUM(CASE WHEN t.mutation_type = 'DB' THEN t.amount ELSE 0 END) AS totalDebit, " +
                  "    COUNT(DISTINCT CAST(YEAR(t.transaction_date) AS VARCHAR) + '-' + CAST(MONTH(t.transaction_date) AS VARCHAR)) AS jumlahBulan, "
                  +
                  "    SUM(CASE WHEN t.mutation_type = 'CR' AND (t.is_excluded = 0 OR t.is_excluded IS NULL) THEN t.amount ELSE 0 END) AS cleanedTotalCredit, "
                  +
                  "    SUM(CASE WHEN t.mutation_type = 'DB' AND (t.is_excluded = 0 OR t.is_excluded IS NULL) THEN t.amount ELSE 0 END) AS cleanedTotalDebit "
                  +
                  "FROM bank_transaction t " +
                  "WHERE t.document_id = :documentId", nativeQuery = true)
      List<Object[]> getRingkasanSaldoByDocumentId(Long documentId);
}
