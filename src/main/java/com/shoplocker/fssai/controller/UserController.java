package com.shoplocker.fssai.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/users")



public class UserController {
    @Autowired

   private  UserService userService;

    @PostMapping
    public User createUser(@Valid @RequestBody User user ){
        return userService.saveUser(user);
    }

    @GetMapping
    public List<User> getAllUser(){
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id){
        return userService.getUserById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Long id ){
        userService.deleteUser(id);

        return "user deleted sucessfully";
    }


}

