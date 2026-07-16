package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.TrademarkDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface TrademarkDocumentRepository extends JpaRepository<TrademarkDocument, Long> {
    Optional<TrademarkDocument> findByShop(Shop shop);
}
