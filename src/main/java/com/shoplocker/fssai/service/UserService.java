package com.shoplocker.fssai.service;

import java.util.List;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.UserRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

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
}
