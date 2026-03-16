package com.example.mutexa_be.repository;

import com.example.mutexa_be.entity.MutationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MutationDocumentRepository extends JpaRepository<MutationDocument, Long> {
}