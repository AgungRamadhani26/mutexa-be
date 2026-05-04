package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.dto.request.MassExcludeRequest;
import com.example.mutexa_be.dto.request.KeywordExcludeRequest;
import com.example.mutexa_be.dto.response.SummaryPerbulanResponse;
import com.example.mutexa_be.dto.response.RingkasanSaldoResponse;
import com.example.mutexa_be.dto.response.DetailTransaksiResponse;
import com.example.mutexa_be.service.DashboardService;
import com.example.mutexa_be.service.ExcelExportService;
import com.example.mutexa_be.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Untuk memudahkan koneksi dari Angular (port 4200)
public class DashboardController {

   private final DashboardService dashboardService;
   private final ExcelExportService excelExportService;

   /**
    * Endpoint untuk ringkasan transaksi per bulan.
    * 
    * @param documentId Harus disertakan agar data yang dihitung HANYA berasal
    *                   dari riwayat transaksi dokumen mutasi spesifik ini saja.
    */
   @GetMapping("/summary-perbulan")
   public ResponseEntity<ApiResponse<List<SummaryPerbulanResponse>>> getSummaryPerbulan(@RequestParam Long documentId) {
      List<SummaryPerbulanResponse> data = dashboardService.getSummaryPerbulan(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data summary per bulan.");
   }

   /**
    * Endpoint untuk ringkasan saldo & arus kas (total dan rata-rata credit/debit).
    * Exclude-aware: transaksi yang di-exclude tidak dihitung.
    */
   @GetMapping("/ringkasan-saldo")
   public ResponseEntity<ApiResponse<RingkasanSaldoResponse>> getRingkasanSaldo(@RequestParam Long documentId) {
      RingkasanSaldoResponse data = dashboardService.getRingkasanSaldo(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data ringkasan saldo.");
   }

   /**
    * Endpoint untuk tabel rincian mutasi (list detail transaksi).
    * 
    * @param documentId ID milik MutationDocument yang statusnya sudah SUCCESS.
    */
   @GetMapping("/detail-transaksi")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getDetailSemuaTransaksi(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getDetailSemuaTransaksi(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data detail transaksi.");
   }

   /**
    * Endpoint khusus untuk mengambil transaksi kategori ADMIN.
    */
   @GetMapping("/admin-transactions")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getAdminTransactions(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getTransactionsByCategory(documentId,
            com.example.mutexa_be.entity.enums.TransactionCategory.ADMIN);
      return ResponseUtil.ok(data, "Berhasil mengambil data transaksi admin.");
   }

   /**
    * Endpoint khusus untuk mengambil transaksi kategori TAX (Pajak).
    */
   @GetMapping("/tax-transactions")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getTaxTransactions(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getTransactionsByCategory(documentId,
            com.example.mutexa_be.entity.enums.TransactionCategory.TAX);
      return ResponseUtil.ok(data, "Berhasil mengambil data transaksi pajak.");
   }

   /**
    * Endpoint khusus untuk mengambil transaksi kategori INTEREST (Bunga).
    */
   @GetMapping("/interest-transactions")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getInterestTransactions(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getTransactionsByCategory(documentId,
            com.example.mutexa_be.entity.enums.TransactionCategory.INTEREST);
      return ResponseUtil.ok(data, "Berhasil mengambil data transaksi bunga.");
   }

   /**
    * Endpoint untuk Top 10 Credit Amount.
    * 
    * @param documentId ID milik MutationDocument
    */
   @GetMapping("/top10-credit")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getTop10CreditAmount(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getTop10CreditAmount(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data top 10 credit.");
   }

   /**
    * Endpoint untuk Top 10 Debit Amount.
    * 
    * @param documentId ID milik MutationDocument
    */
   @GetMapping("/top10-debit")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getTop10DebitAmount(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getTop10DebitAmount(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data top 10 debit.");
   }

   @GetMapping("/top10-credit-cleaned")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getTop10CreditAmountCleaned(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getTop10CreditAmountCleaned(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data top 10 credit cleaned.");
   }

   @GetMapping("/top10-debit-cleaned")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getTop10DebitAmountCleaned(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getTop10DebitAmountCleaned(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data top 10 debit cleaned.");
   }

   /**
    * Endpoint untuk Top 10 Credit Frequency.
    * 
    * @param documentId ID milik MutationDocument
    */
   @GetMapping("/top10-credit-freq")
   public ResponseEntity<ApiResponse<List<com.example.mutexa_be.dto.response.TopFreqResponse>>> getTop10CreditFreq(
         @RequestParam Long documentId) {
      List<com.example.mutexa_be.dto.response.TopFreqResponse> data = dashboardService.getTop10CreditFreq(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data frekuensi credit top 10.");
   }

   /**
    * Endpoint untuk Top 10 Debit Frequency.
    *
    * @param documentId ID milik MutationDocument
    */
   @GetMapping("/top10-debit-freq")
   public ResponseEntity<ApiResponse<List<com.example.mutexa_be.dto.response.TopFreqResponse>>> getTop10DebitFreq(
         @RequestParam Long documentId) {
      List<com.example.mutexa_be.dto.response.TopFreqResponse> data = dashboardService.getTop10DebitFreq(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data frekuensi debit top 10.");
   }

   @GetMapping("/top10-credit-freq-cleaned")
   public ResponseEntity<ApiResponse<List<com.example.mutexa_be.dto.response.TopFreqResponse>>> getTop10CreditFreqCleaned(
         @RequestParam Long documentId) {
      List<com.example.mutexa_be.dto.response.TopFreqResponse> data = dashboardService
            .getTop10CreditFreqCleaned(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data frekuensi kredit top 10 (cleaned).");
   }

   @GetMapping("/top10-debit-freq-cleaned")
   public ResponseEntity<ApiResponse<List<com.example.mutexa_be.dto.response.TopFreqResponse>>> getTop10DebitFreqCleaned(
         @RequestParam Long documentId) {
      List<com.example.mutexa_be.dto.response.TopFreqResponse> data = dashboardService
            .getTop10DebitFreqCleaned(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data frekuensi debit top 10 (cleaned).");
   }

   @GetMapping("/export-excel")
   public ResponseEntity<InputStreamResource> downloadExcel(
         @RequestParam Long documentId,
         @RequestParam(required = false) String accountName,
         @RequestParam(required = false) String month,
         @RequestParam(required = false) String flag,
         @RequestParam(required = false) String excludeStatus) throws IOException {
      List<DetailTransaksiResponse> data = dashboardService.getDetailSemuaTransaksi(documentId);

      // Apply Exclude Status Filter
      boolean isExcludeFiltered = false;
      if (excludeStatus != null && !excludeStatus.trim().isEmpty() && !excludeStatus.equals("ALL")) {
         isExcludeFiltered = true;
         if (excludeStatus.equals("ACTIVE")) {
            data = data.stream().filter(tx -> tx.getIsExcluded() == null || !tx.getIsExcluded()).toList();
         } else if (excludeStatus.equals("EXCLUDED")) {
            data = data.stream().filter(tx -> tx.getIsExcluded() != null && tx.getIsExcluded()).toList();
         }
      }

      // Apply Month Filter
      if (month != null && !month.trim().isEmpty() && !month.equals("ALL")) {
         data = data.stream().filter(tx -> tx.getTanggal() != null && tx.getTanggal().startsWith(month)).toList();
      }

      // Apply Flag Filter
      boolean isFlagFiltered = false;
      if (flag != null && !flag.trim().isEmpty() && !flag.equals("ALL")) {
         isFlagFiltered = true;
         data = data.stream().filter(tx -> tx.getFlag() != null && tx.getFlag().equalsIgnoreCase(flag)).toList();
      }

      // Tampilkan kolom saldo HANYA jika tidak ada filter flag DAN tidak ada filter
      // exclude
      boolean showSaldo = !isFlagFiltered && !isExcludeFiltered;

      ByteArrayInputStream in = excelExportService.exportDetailTransaksiToExcel(data, showSaldo, flag);

      // Build dynamic filename
      String fileName = buildExportFileName(accountName, month, flag, excludeStatus);

      HttpHeaders headers = new HttpHeaders();
      headers.add("Content-Disposition", "attachment; filename=" + fileName);

      return ResponseEntity
            .ok()
            .headers(headers)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(new InputStreamResource(in));
   }

   /**
    * Membangun nama file Excel dinamis berdasarkan filter yang diterapkan.
    * Format: detail_transaksi_[NamaPemilik]_[Filter].xlsx
    */
   private String buildExportFileName(String accountName, String month, String flag, String excludeStatus) {
      StringBuilder sb = new StringBuilder("detail_transaksi");

      // Tambahkan nama pemilik rekening (hilangkan spasi)
      if (accountName != null && !accountName.trim().isEmpty()) {
         String cleanName = accountName.trim().replaceAll("\\s+", "");
         sb.append("_").append(cleanName);
      }

      // Cek apakah ada filter aktif
      boolean hasMonthFilter = month != null && !month.trim().isEmpty() && !month.equals("ALL");
      boolean hasFlagFilter = flag != null && !flag.trim().isEmpty() && !flag.equals("ALL");
      boolean hasExcludeFilter = excludeStatus != null && !excludeStatus.trim().isEmpty()
            && !excludeStatus.equals("ALL");

      if (!hasMonthFilter && !hasFlagFilter && !hasExcludeFilter) {
         sb.append("_Lengkap");
      } else {
         // Filter bulan: 2026-01 → Jan2026
         if (hasMonthFilter) {
            String[] parts = month.split("-");
            if (parts.length == 2) {
               String[] monthNames = { "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
                     "Jul", "Ags", "Sep", "Okt", "Nov", "Des" };
               int monthIdx = Integer.parseInt(parts[1]) - 1;
               if (monthIdx >= 0 && monthIdx < 12) {
                  sb.append("_").append(monthNames[monthIdx]).append(parts[0]);
               }
            }
         }

         // Filter flag: CR → Kredit, DB → Debit
         if (hasFlagFilter) {
            sb.append("_").append("CR".equalsIgnoreCase(flag) ? "Kredit" : "Debit");
         }

         // Filter exclude: ACTIVE → Aktif, EXCLUDED → Excluded
         if (hasExcludeFilter) {
            sb.append("_").append("ACTIVE".equals(excludeStatus) ? "Aktif" : "Excluded");
         }
      }

      sb.append(".xlsx");
      return sb.toString();
   }

   @PostMapping("/toggle-exclude/{id}")
   public ResponseEntity<ApiResponse<String>> toggleExclude(
         @org.springframework.web.bind.annotation.PathVariable Long id) {
      dashboardService.toggleExclude(id);
      return ResponseUtil.ok("Success toggle", "Berhasil mengubah status exclude transaksi.");
   }

   @PostMapping("/mass-toggle-exclude")
   public ResponseEntity<ApiResponse<String>> massToggleExclude(@RequestBody MassExcludeRequest request) {
      dashboardService.massToggleExclude(request.getDocumentId(), request.getCategory(), request.getIsExcluded());
      return ResponseUtil.ok("Success mass toggle", "Berhasil melakukan exclude/include massal.");
   }

   /**
    * Endpoint untuk mengambil transaksi anomali tipe Credit (CR).
    */
   @GetMapping("/anomaly-credit")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getAnomalyCreditTransactions(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getAnomalyTransactions(documentId,
            com.example.mutexa_be.entity.enums.MutationType.CR);
      return ResponseUtil.ok(data, "Berhasil mengambil data anomali credit.");
   }

   /**
    * Endpoint untuk mengambil transaksi anomali tipe Debit (DB).
    */
   @GetMapping("/anomaly-debit")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getAnomalyDebitTransactions(
         @RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getAnomalyTransactions(documentId,
            com.example.mutexa_be.entity.enums.MutationType.DB);
      return ResponseUtil.ok(data, "Berhasil mengambil data anomali debit.");
   }

   /**
    * Pencarian transaksi berdasarkan keyword (nama afiliasi, no rekening, dsb).
    */
   @GetMapping("/search-keyword")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> searchTransactionsByKeyword(
         @RequestParam Long documentId,
         @RequestParam String keyword) {
      List<DetailTransaksiResponse> data = dashboardService.searchTransactionsByKeyword(documentId, keyword);
      return ResponseUtil.ok(data, "Berhasil mencari transaksi berdasarkan keyword.");
   }

   /**
    * Mass toggle exclude/include berdasarkan keyword.
    */
   @PostMapping("/mass-toggle-keyword")
   public ResponseEntity<ApiResponse<String>> massToggleKeywordExclude(@RequestBody KeywordExcludeRequest request) {
      dashboardService.massToggleKeywordExclude(request.getDocumentId(), request.getKeyword(),
            request.getIsExcluded());
      return ResponseUtil.ok("Success", "Berhasil mengubah status exclude transaksi berdasarkan keyword.");
   }
}
