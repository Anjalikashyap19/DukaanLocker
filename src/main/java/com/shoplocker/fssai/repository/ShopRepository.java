package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    boolean existsByMobile(String mobile);

    List<Shop> findByOwnerId(Long ownerId);

    Optional<Shop> findByIdAndOwnerId(Long id, Long ownerId);
}
