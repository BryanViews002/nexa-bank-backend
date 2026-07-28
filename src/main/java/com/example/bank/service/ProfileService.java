package com.example.bank.service;

import com.example.bank.dto.ProfileDto;
import com.example.bank.dto.UserResponse;
import com.example.bank.dto.BankMapper;
import com.example.bank.entity.User;
import com.example.bank.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BankMapper bankMapper;
    private final NotificationService notificationService;

    public ProfileService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            BankMapper bankMapper,
            NotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bankMapper = bankMapper;
        this.notificationService = notificationService;
    }

    public UserResponse get(User user) {
        return bankMapper.toUserResponse(currentUser(user));
    }

    @Transactional
    public UserResponse update(User user, ProfileDto.UpdateRequest request) {
        User current = currentUser(user);
        Long currentId = current.getId();
        userRepository.findByEmail(request.email()).ifPresent(existing -> {
            if (!existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("Email is already in use");
            }
        });
        current.setFullName(request.fullName().trim());
        current.setEmail(request.email().trim().toLowerCase());
        current.setPhoneNumber(blankToNull(request.phoneNumber()));
        current.setAddress(blankToNull(request.address()));
        current = userRepository.save(current);
        notificationService.notify(
                current,
                com.example.bank.entity.Notification.NotificationType.SECURITY,
                "Profile updated",
                "Your personal profile was updated.",
                "USER",
                current.getId()
        );
        return bankMapper.toUserResponse(current);
    }

    @Transactional
    public void changePassword(User user, ProfileDto.PasswordChangeRequest request) {
        User current = currentUser(user);
        if (!passwordEncoder.matches(request.currentPassword(), current.getPasswordHash())) {
            throw new AccessDeniedException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), current.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        current.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(current);
        notificationService.notify(
                current,
                com.example.bank.entity.Notification.NotificationType.SECURITY,
                "Password changed",
                "Your password was changed successfully.",
                "USER",
                current.getId()
        );
    }

    private User currentUser(User user) {
        return userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
