package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.ManagerShopAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManagerShopAssignmentRepository extends JpaRepository<ManagerShopAssignment, Long> {

    boolean existsByManagerIdAndShopIdAndActiveTrue(Long managerId, Long shopId);

    List<ManagerShopAssignment> findByManagerIdAndActiveTrue(Long managerId);

    List<ManagerShopAssignment> findByManagerIdAndAssignedByAdminId(Long managerId, Long adminId);

    Optional<ManagerShopAssignment> findByManagerIdAndShopId(Long managerId, Long shopId);
}
