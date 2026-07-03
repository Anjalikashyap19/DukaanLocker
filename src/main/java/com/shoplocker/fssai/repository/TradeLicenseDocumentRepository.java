package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.TradeLicenseDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface TradeLicenseDocumentRepository extends JpaRepository<TradeLicenseDocument, Long> {
    Optional<TradeLicenseDocument> findByShop(Shop shop);
}
