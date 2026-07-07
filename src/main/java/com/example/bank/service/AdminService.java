// src/main/java/com/example/bank/service/AdminService.java
package com.example.bank.service;

import com.example.bank.entity.User;
import com.example.bank.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {
    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void unlockUser(Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setLocked(false);
        user.setFailedLoginCount(0);
        userRepository.save(user);
    }
}