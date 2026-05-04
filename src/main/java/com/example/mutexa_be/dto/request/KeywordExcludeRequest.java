package com.example.mutexa_be.dto.request;

import lombok.Data;

@Data
public class KeywordExcludeRequest {
    private Long documentId;
    private String keyword;
    private Boolean isExcluded;
}
