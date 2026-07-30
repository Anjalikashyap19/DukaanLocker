package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shoplocker.fssai.entity.GSTDocument;
import java.util.Optional;
import com.shoplocker.fssai.entity.Shop;

@Repository
public interface GSTDocumentRepository extends JpaRepository<GSTDocument,Long>{

    Optional<GSTDocument>findByShop(Shop shop);


}
