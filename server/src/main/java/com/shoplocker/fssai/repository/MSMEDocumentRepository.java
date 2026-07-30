package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.MSMEDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface MSMEDocumentRepository extends JpaRepository<MSMEDocument, Long> {
    Optional<MSMEDocument> findByShop(Shop shop);
}
