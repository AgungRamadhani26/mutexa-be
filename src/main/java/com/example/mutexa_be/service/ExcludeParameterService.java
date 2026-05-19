package com.example.mutexa_be.service;

import com.example.mutexa_be.dto.request.ExcludeParameterRequest;
import com.example.mutexa_be.dto.response.ExcludeParameterResponse;
import com.example.mutexa_be.entity.ExcludeParameter;
import com.example.mutexa_be.entity.enums.ParameterStatus;
import com.example.mutexa_be.repository.ExcludeParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcludeParameterService {

    private final ExcludeParameterRepository repository;

    @Transactional(readOnly = true)
    public List<ExcludeParameterResponse> getAllParameters() {
        return repository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ExcludeParameterResponse createParameter(ExcludeParameterRequest request) {
        if (request.getKeyword() == null || request.getKeyword().trim().isEmpty()) {
            throw new IllegalArgumentException("Kata kunci tidak boleh kosong!");
        }

        String keywordClean = request.getKeyword().trim().toUpperCase();

        if (repository.findByKeywordIgnoreCase(keywordClean).isPresent()) {
            throw new IllegalArgumentException("Kata kunci '" + keywordClean + "' sudah terdaftar sebelumnya!");
        }

        ExcludeParameter parameter = ExcludeParameter.builder()
                .keyword(keywordClean)
                .status(ParameterStatus.PENDING)
                .build();

        ExcludeParameter saved = repository.save(parameter);
        log.info("Berhasil mengajukan parameter exclude baru: {}", keywordClean);
        return mapToResponse(saved);
    }

    @Transactional
    public ExcludeParameterResponse approveParameter(Long id) {
        ExcludeParameter parameter = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Parameter tidak ditemukan!"));

        if (parameter.getStatus() == ParameterStatus.ACTIVE) {
            throw new IllegalArgumentException("Parameter sudah berstatus aktif!");
        }

        parameter.setStatus(ParameterStatus.ACTIVE);
        parameter.setActivatedAt(LocalDateTime.now());

        ExcludeParameter updated = repository.save(parameter);
        log.info("Berhasil menyetujui parameter exclude: {}", parameter.getKeyword());
        return mapToResponse(updated);
    }

    @Transactional
    public ExcludeParameterResponse deactivateParameter(Long id) {
        ExcludeParameter parameter = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Parameter tidak ditemukan!"));

        if (parameter.getStatus() == ParameterStatus.INACTIVE) {
            throw new IllegalArgumentException("Parameter sudah dinonaktifkan!");
        }

        parameter.setStatus(ParameterStatus.INACTIVE);

        ExcludeParameter updated = repository.save(parameter);
        log.info("Berhasil menonaktifkan parameter exclude: {}", parameter.getKeyword());
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<String> getActiveKeywords() {
        return repository.findByStatus(ParameterStatus.ACTIVE).stream()
                .map(ExcludeParameter::getKeyword)
                .collect(Collectors.toList());
    }

    private ExcludeParameterResponse mapToResponse(ExcludeParameter parameter) {
        return ExcludeParameterResponse.builder()
                .id(parameter.getId())
                .keyword(parameter.getKeyword())
                .status(parameter.getStatus())
                .createdAt(parameter.getCreatedAt())
                .activatedAt(parameter.getActivatedAt())
                .build();
    }
}
