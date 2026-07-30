package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.IECDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface IECDocumentRepository extends JpaRepository<IECDocument, Long> {
    Optional<IECDocument> findByShop(Shop shop);
}
