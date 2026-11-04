package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findTopByUserIdAndShopIdAndDocumentTypeAndDocumentIdAndStatusOrderByCreatedAtDesc(
            Long userId, Long shopId, String documentType, Long documentId, String status);

    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
