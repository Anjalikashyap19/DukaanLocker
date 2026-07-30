package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.shoplocker.fssai.entity.LabourLicenseDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface LabourLicenseDocumentRepository extends JpaRepository<LabourLicenseDocument, Long> {
    Optional<LabourLicenseDocument> findByShop(Shop shop);
}
