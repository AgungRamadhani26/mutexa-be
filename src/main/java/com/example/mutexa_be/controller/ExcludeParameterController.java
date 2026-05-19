package com.example.mutexa_be.controller;

import com.example.mutexa_be.base.ApiResponse;
import com.example.mutexa_be.dto.request.ExcludeParameterRequest;
import com.example.mutexa_be.dto.response.ExcludeParameterResponse;
import com.example.mutexa_be.service.ExcludeParameterService;
import com.example.mutexa_be.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exclude-parameters")
@RequiredArgsConstructor
public class ExcludeParameterController {

    private final ExcludeParameterService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExcludeParameterResponse>>> getAllParameters() {
        List<ExcludeParameterResponse> response = service.getAllParameters();
        return ResponseUtil.ok(response, "Berhasil mengambil daftar parameter exclude.");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExcludeParameterResponse>> createParameter(
            @RequestBody ExcludeParameterRequest request) {
        ExcludeParameterResponse response = service.createParameter(request);
        return ResponseUtil.created(response, "Parameter '" + response.getKeyword() + "' berhasil diajukan.");
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExcludeParameterResponse>> approveParameter(@PathVariable Long id) {
        ExcludeParameterResponse response = service.approveParameter(id);
        return ResponseUtil.ok(response, "Parameter '" + response.getKeyword() + "' berhasil disetujui.");
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExcludeParameterResponse>> deactivateParameter(@PathVariable Long id) {
        ExcludeParameterResponse response = service.deactivateParameter(id);
        return ResponseUtil.ok(response, "Parameter '" + response.getKeyword() + "' berhasil dinonaktifkan.");
    }
}
