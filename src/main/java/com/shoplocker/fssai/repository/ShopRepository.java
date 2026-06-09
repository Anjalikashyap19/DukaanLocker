package com.shoplocker.fssai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shoplocker.fssai.entity.Shop;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

}