package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailId(String emailId);

    boolean existsByEmailId(String emailId);

    boolean existsByMobileNumber(String mobileNumber);

    Optional<User> findByMobileNumber(String mobileNumber);

    List<User> findByCreatedByAdminIdAndRole(Long adminId, Role role);
}
