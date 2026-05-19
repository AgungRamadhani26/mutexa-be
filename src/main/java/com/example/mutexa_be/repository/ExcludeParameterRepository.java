package com.example.mutexa_be.repository;

import com.example.mutexa_be.entity.ExcludeParameter;
import com.example.mutexa_be.entity.enums.ParameterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExcludeParameterRepository extends JpaRepository<ExcludeParameter, Long> {
    List<ExcludeParameter> findByStatus(ParameterStatus status);
    Optional<ExcludeParameter> findByKeywordIgnoreCase(String keyword);
}
