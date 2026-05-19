package com.example.mutexa_be.dto.response;

import com.example.mutexa_be.entity.enums.ParameterStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ExcludeParameterResponse {
    private Long id;
    private String keyword;
    private ParameterStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
}
