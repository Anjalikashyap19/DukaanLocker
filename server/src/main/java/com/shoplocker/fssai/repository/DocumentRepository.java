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

    /**
     * Rows holding byte-identical file content to {@code contentHash} that live
     * on a <b>different</b> shop of the <b>same</b> owner.
     *
     * <p>Ownership is resolved with a subquery on {@code Shop} rather than passed
     * in as an id, so the caller cannot accidentally pass the id of an
     * authenticated <i>manager</i> and judge the file against the wrong account.
     * Comparing against the owner's whole portfolio is what makes this work for
     * manager-driven uploads too.
     *
     * <p>Rows written before the content_hash column existed are null and never
     * match, so pre-existing documents are unaffected.
     */
    @Query("select d from Document d where d.contentHash = :contentHash "
            + "and d.shop.id <> :shopId "
            + "and d.shop.owner.id = (select s.owner.id from Shop s where s.id = :shopId)")
    List<Document> findByContentHashForOtherShopOfSameOwner(String contentHash, Long shopId);
}
