package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.Document;
import com.shoplocker.fssai.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByShopId(Long shopId);

    /**
     * Documents that carry an expiry date, with their shop and the shop's owner
     * fetched in the same query.
     *
     * <p>{@code Document.shop} is LAZY, so a plain {@code findAll()} outside a
     * transaction hands back uninitializable proxies and touching
     * {@code doc.getShop().getOwner()} throws LazyInitializationException. The
     * schedulers run without an open session, so they must fetch the graph up
     * front.
     */
    @Query("select d from Document d join fetch d.shop s join fetch s.owner "
            + "where d.expiryDate is not null")
    List<Document> findAllWithShopAndOwner();

    Optional<Document> findByShopIdAndDocumentType(Long shopId, DocumentType documentType);

    boolean existsByDocumentNumberAndDocumentType(String documentNumber, DocumentType documentType);

    Optional<Document> findByDocumentNumberAndDocumentType(String documentNumber, DocumentType documentType);
}
