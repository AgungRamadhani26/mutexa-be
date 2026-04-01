package com.example.mutexa_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountWithDocumentsResponse {
    private Long id;
    private String accountNumber;
    private String accountName;
    private String bankName;
    private Long documentCount;
}
