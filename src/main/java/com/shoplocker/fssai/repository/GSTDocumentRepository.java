package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shoplocker.fssai.entity.GSTDocument;


@Repository
public interface GSTDocumentRepository extends JpaRepository<GSTDocument,Long>{


}
