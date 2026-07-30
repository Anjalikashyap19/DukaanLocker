package com.shoplocker.fssai.service;

import java.util.List;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.dto.UserResponse;
import com.shoplocker.fssai.dto.ShopResponse;
import com.shoplocker.fssai.service.ShopService;
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopService shopService;

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new FssaiException("User not found: " + id, FailureCode.USER_NOT_FOUND));
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }


    public UserResponse toUserResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getUserName(),
                user.getMobileNumber(),
                user.getEmailId(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                null
        );
    }

    public UserResponse toUserDetailResponse(User user) {

        List<ShopResponse> shops = user.getShops()
                .stream()
                .map(shopService::toShopResponse)
                .toList();


        return new UserResponse(
                user.getId(),
                user.getUserName(),
                user.getMobileNumber(),
                user.getEmailId(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                shops
        );
    }
}
