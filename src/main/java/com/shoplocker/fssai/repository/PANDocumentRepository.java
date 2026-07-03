package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.PANDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface PANDocumentRepository extends JpaRepository<PANDocument, Long> {
    Optional<PANDocument> findByShop(Shop shop);
}
