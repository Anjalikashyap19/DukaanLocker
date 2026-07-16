package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.ShopInsuranceDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface ShopInsuranceDocumentRepository extends JpaRepository<ShopInsuranceDocument, Long> {
    Optional<ShopInsuranceDocument> findByShop(Shop shop);
}
