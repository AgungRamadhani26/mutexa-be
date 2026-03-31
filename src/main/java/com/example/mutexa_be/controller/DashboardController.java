package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.dto.response.SummaryPerbulanResponse;
import com.example.mutexa_be.service.DashboardService;
import com.example.mutexa_be.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Untuk memudahkan koneksi dari Angular (port 4200)
public class DashboardController {

   private final DashboardService dashboardService;

   @GetMapping("/summary-perbulan")
   public ResponseEntity<ApiResponse<List<SummaryPerbulanResponse>>> getSummaryPerbulan() {
      List<SummaryPerbulanResponse> data = dashboardService.getSummaryPerbulan();
      return ResponseUtil.ok(data, "Berhasil mengambil data summary per bulan.");
   }
}
