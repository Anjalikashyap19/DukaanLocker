package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.FssaiFoodLicenseDocument;
import com.shoplocker.fssai.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FssaiFoodLicenseDocumentRepository extends JpaRepository<FssaiFoodLicenseDocument, Long> {
    Optional<FssaiFoodLicenseDocument> findByShop(Shop shop);
}
