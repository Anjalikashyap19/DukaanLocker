package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.RenewalOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RenewalOrderRepository extends JpaRepository<RenewalOrder, Long> {

    List<RenewalOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<RenewalOrder> findAllByOrderByCreatedAtDesc();

    Optional<RenewalOrder> findFirstByUserIdAndShopIdAndDocumentTypeAndStatusIn(
            Long userId, Long shopId,
            com.shoplocker.fssai.entity.DocumentType documentType,
            List<String> statuses);
}
