package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.ProfessionalTaxDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface ProfessionalTaxDocumentRepository extends JpaRepository<ProfessionalTaxDocument, Long> {
    Optional<ProfessionalTaxDocument> findByShop(Shop shop);
}
