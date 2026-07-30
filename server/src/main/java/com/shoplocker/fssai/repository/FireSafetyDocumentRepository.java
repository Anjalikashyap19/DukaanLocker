package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.FireSafetyDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface FireSafetyDocumentRepository extends JpaRepository<FireSafetyDocument, Long> {
    Optional<FireSafetyDocument> findByShop(Shop shop);
}
