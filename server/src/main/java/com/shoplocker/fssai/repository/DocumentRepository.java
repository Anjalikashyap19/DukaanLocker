package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.Document;
import com.shoplocker.fssai.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByShopId(Long shopId);

    Optional<Document> findByShopIdAndDocumentType(Long shopId, DocumentType documentType);

    boolean existsByDocumentNumberAndDocumentType(String documentNumber, DocumentType documentType);
}
