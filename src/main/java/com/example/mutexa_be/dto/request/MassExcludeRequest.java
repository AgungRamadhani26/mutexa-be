package com.example.mutexa_be.dto.request;

import lombok.Data;

@Data
public class MassExcludeRequest {
    private Long documentId;
    private String category; // e.g. "ADMIN", "TAX", "INTEREST", "ANOMALY_CR", "ANOMALY_DB"
    private Boolean isExcluded;
}
