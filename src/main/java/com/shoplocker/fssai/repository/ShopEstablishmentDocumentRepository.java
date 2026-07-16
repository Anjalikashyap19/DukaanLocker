package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.ShopEstablishmentDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface ShopEstablishmentDocumentRepository extends JpaRepository<ShopEstablishmentDocument, Long> {
    Optional<ShopEstablishmentDocument> findByShop(Shop shop);
}
