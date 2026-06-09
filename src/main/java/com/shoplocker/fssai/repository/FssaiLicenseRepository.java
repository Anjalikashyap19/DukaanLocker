package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.FssaiLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FssaiLicenseRepository extends JpaRepository<FssaiLicense, Long> {

    Optional<FssaiLicense> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);
}
