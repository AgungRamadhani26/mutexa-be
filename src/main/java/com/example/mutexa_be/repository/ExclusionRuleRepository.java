package com.example.mutexa_be.repository;

import com.example.mutexa_be.entity.ExclusionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExclusionRuleRepository extends JpaRepository<ExclusionRule, Long> {
}