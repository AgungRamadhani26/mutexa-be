package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
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

   @GetMapping("/export-excel")
   public ResponseEntity<InputStreamResource> downloadExcel(
         @RequestParam Long documentId,
         @RequestParam(required = false) String month,
         @RequestParam(required = false) String flag) throws IOException {
      List<DetailTransaksiResponse> data = dashboardService.getDetailSemuaTransaksi(documentId);
      
      // Filter out excluded items from the excel export
      data = data.stream().filter(tx -> tx.getIsExcluded() == null || !tx.getIsExcluded()).toList();

      // Apply Month Filter
      if (month != null && !month.trim().isEmpty() && !month.equals("ALL")) {
          data = data.stream().filter(tx -> tx.getTanggal() != null && tx.getTanggal().startsWith(month)).toList();
      }

      // Apply Flag Filter 
      boolean showSaldo = false;
      if (flag != null && !flag.trim().isEmpty() && !flag.equals("ALL")) {
          data = data.stream().filter(tx -> tx.getFlag() != null && tx.getFlag().equalsIgnoreCase(flag)).toList();
      } else {
          // Hanya tampilkan kolom saldo jika filter flag adalah SEMUA (ALL) atau tidak ada
          showSaldo = true;
      }

      ByteArrayInputStream in = excelExportService.exportDetailTransaksiToExcel(data, showSaldo);

      HttpHeaders headers = new HttpHeaders();
      headers.add("Content-Disposition", "attachment; filename=detail_transaksi.xlsx");

      return ResponseEntity
            .ok()
            .headers(headers)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(new InputStreamResource(in));
   }

   @PostMapping("/toggle-exclude/{id}")
   public ResponseEntity<ApiResponse<String>> toggleExclude(@org.springframework.web.bind.annotation.PathVariable Long id) {
      dashboardService.toggleExclude(id);
      return ResponseUtil.ok("Success toggle", "Berhasil mengubah status exclude transaksi.");
   }
}
