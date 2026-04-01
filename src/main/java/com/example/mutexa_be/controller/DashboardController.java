package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.dto.response.SummaryPerbulanResponse;
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
    * @param documentId Harus disertakan agar data yang dihitung HANYA berasal
    *                   dari riwayat transaksi dokumen mutasi spesifik ini saja.
    */
   @GetMapping("/summary-perbulan")
   public ResponseEntity<ApiResponse<List<SummaryPerbulanResponse>>> getSummaryPerbulan(@RequestParam Long documentId) {
      List<SummaryPerbulanResponse> data = dashboardService.getSummaryPerbulan(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data summary per bulan.");
   }

   /**
    * Endpoint untuk tabel rincian mutasi (list detail transaksi).
    * @param documentId ID milik MutationDocument yang statusnya sudah SUCCESS.
    */
   @GetMapping("/detail-transaksi")
   public ResponseEntity<ApiResponse<List<DetailTransaksiResponse>>> getDetailSemuaTransaksi(@RequestParam Long documentId) {
      List<DetailTransaksiResponse> data = dashboardService.getDetailSemuaTransaksi(documentId);
      return ResponseUtil.ok(data, "Berhasil mengambil data detail transaksi.");
   }

   @GetMapping("/export-excel")
   public ResponseEntity<InputStreamResource> downloadExcel(@RequestParam Long documentId) throws IOException {
      List<DetailTransaksiResponse> data = dashboardService.getDetailSemuaTransaksi(documentId);
      ByteArrayInputStream in = excelExportService.exportDetailTransaksiToExcel(data);

      HttpHeaders headers = new HttpHeaders();
      headers.add("Content-Disposition", "attachment; filename=detail_transaksi.xlsx");

      return ResponseEntity
            .ok()
            .headers(headers)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(new InputStreamResource(in));
   }
}
