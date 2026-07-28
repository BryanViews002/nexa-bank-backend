package com.example.bank.service;

import com.example.bank.entity.User;
import com.example.bank.exception.KycRequiredException;
import com.example.bank.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class KycGuardService {

    private final UserRepository userRepository;

    public KycGuardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void requireApproved(User user) {
        User.KycStatus status = getCurrentStatus(user);
        if (status != User.KycStatus.APPROVED) {
            throw new KycRequiredException(status);
        }
    }

    public User.KycStatus getCurrentStatus(User user) {
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        return userRepository.findById(user.getId())
                .map(User::getKycStatus)
                .map(status -> status == null ? User.KycStatus.NOT_SUBMITTED : status)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
    }
}
