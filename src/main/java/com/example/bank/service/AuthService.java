package com.example.bank.service;

import com.example.bank.dto.AuthDto;
import com.example.bank.entity.Role;
import com.example.bank.entity.User;
import com.example.bank.repository.RoleRepository;
import com.example.bank.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
@Slf4j
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final AuditService auditService;
    private final AccountService accountService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, OtpService otpService,
                       AuditService auditService, AccountService accountService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.auditService = auditService;
        this.accountService = accountService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user details for username: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        log.debug("User found: {} (enabled: {}, locked: {})",
                username, user.isEnabled(), user.isLocked());
        return user;
    }

    public User findUserByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found for email: {}", email);
                    return new RuntimeException("Email not found");
                });
    }

    public User findUserByUsername(String username) {
        log.debug("Finding user by username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found for username: {}", username);
                    return new RuntimeException("Username not found");
                });
    }

    public User register(AuthDto.RegisterRequest request) {
        log.info("Registration attempt for username: {} and email: {}", request.getUsername(), request.getEmail());

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            log.warn("Registration failed: Username {} already taken", request.getUsername());
            throw new RuntimeException("Username taken");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed: Email {} already taken", request.getEmail());
            throw new RuntimeException("Email taken");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> {
                    log.error("Default ROLE_USER not found in database");
                    return new RuntimeException("Default role not found");
                });

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(userRole);
        user.setEnabled(true);
        user.setFailedLoginCount(0);
        user.setLocked(false);

        try {
            User saved = userRepository.save(user);
            auditService.log(saved.getId(), "USER_REGISTERED", Map.of("username", saved.getUsername()));
            // Auto-create SAVINGS account with $25 bonus for first account
            boolean isFirstAccount = accountService.getUserAccounts(saved).isEmpty();
            accountService.openAccount(saved, "SAVINGS", isFirstAccount ? 25.0 : 0.0);
            log.info("User registered successfully with Nexa: {} with ID: {}", saved.getUsername(), saved.getId());
            return saved;
        } catch (Exception e) {
            log.error("Error saving user during registration: {}", e.getMessage(), e);
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);

        User user = findUserByEmail(email);
        try {
            otpService.generateOtp(user, "PASSWORD_RESET");
            auditService.log(user.getId(), "PASSWORD_RESET_REQUESTED",
                    Map.of("email", email, "username", user.getUsername()));
            log.info("Password reset OTP sent by Nexa for user: {} ({})", user.getUsername(), email);
        } catch (Exception e) {
            log.error("Error generating password reset OTP for {}: {}", email, e.getMessage());
            throw new RuntimeException("Failed to generate reset OTP");
        }
    }

    public void confirmPasswordReset(String email, String code, String newPassword) {
        log.info("Password reset confirmation attempt for email: {}", email);

        User user = findUserByEmail(email);
        try {
            if (otpService.verifyOtp(user, code, "PASSWORD_RESET")) {
                user.setPasswordHash(passwordEncoder.encode(newPassword));
                user.setFailedLoginCount(0);
                user.setLocked(false);

                userRepository.save(user);
                auditService.log(user.getId(), "PASSWORD_RESET_COMPLETED",
                        Map.of("email", email, "username", user.getUsername()));
                log.info("Password reset completed successfully for user: {} ({})", user.getUsername(), email);
            } else {
                log.warn("Password reset failed: Invalid OTP for user: {} ({})", user.getUsername(), email);
                auditService.log(user.getId(), "PASSWORD_RESET_FAILED",
                        Map.of("email", email, "username", user.getUsername(), "reason", "Invalid OTP"));
                throw new RuntimeException("Invalid OTP");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during password reset confirmation for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Password reset failed");
        }
    }

    public void incrementFailedLoginCount(String username) {
        log.debug("Incrementing failed login count for username: {}", username);

        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            int newCount = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(newCount);

            if (newCount >= 3) {
                user.setLocked(true);
                log.warn("User account locked due to {} failed login attempts: {}", newCount, username);
            }

            try {
                userRepository.save(user);
                auditService.log(user.getId(), "LOGIN_FAILED",
                        Map.of("attempt", newCount, "username", username, "locked", user.isLocked()));
                log.info("Failed login count updated for user: {} (count: {}, locked: {})",
                        username, newCount, user.isLocked());
            } catch (Exception e) {
                log.error("Error updating failed login count for {}: {}", username, e.getMessage());
            }
        }, () -> {
            log.warn("Attempted to increment failed login count for non-existent user: {}", username);
        });
    }

    public void resetFailedLoginCount(User user) {
        if (user.getFailedLoginCount() > 0 || user.isLocked()) {
            log.debug("Resetting failed login count for user: {} (was: {}, locked: {})",
                    user.getUsername(), user.getFailedLoginCount(), user.isLocked());

            user.setFailedLoginCount(0);
            if (user.isLocked()) {
                user.setLocked(false);
                log.info("User account unlocked: {}", user.getUsername());
            }

            try {
                userRepository.save(user);
                auditService.log(user.getId(), "LOGIN_SUCCESS",
                        Map.of("username", user.getUsername(), "unlocked", !user.isLocked()));
                log.debug("Failed login count reset successfully for user: {}", user.getUsername());
            } catch (Exception e) {
                log.error("Error resetting failed login count for {}: {}", user.getUsername(), e.getMessage());
            }
        }
    }

    public boolean isUserActive(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.isEnabled() && !user.isLocked())
                .orElse(false);
    }

    public void unlockUser(String username) {
        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            if (user.isLocked()) {
                user.setLocked(false);
                user.setFailedLoginCount(0);
                userRepository.save(user);
                auditService.log(user.getId(), "USER_UNLOCKED",
                        Map.of("username", username, "unlockedBy", "ADMIN"));
                log.info("User manually unlocked: {}", username);
            }
        }, () -> {
            log.warn("Attempted to unlock non-existent user: {}", username);
        });
    }
}