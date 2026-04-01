package com.example.mutexa_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentListResponse {
    private Long id;
    private String fileName;
    private String fileType;
    private String status;
    private String errorMessage;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime createdAt;
}
