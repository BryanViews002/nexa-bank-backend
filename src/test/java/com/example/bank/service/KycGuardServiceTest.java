package com.example.bank.service;

import com.example.bank.entity.User;
import com.example.bank.exception.KycRequiredException;
import com.example.bank.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycGuardServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final KycGuardService kycGuardService = new KycGuardService(userRepository);

    @Test
    void allowsTransferWhenDatabaseStatusWasApprovedAfterLogin() {
        User sessionUser = user(10L, User.KycStatus.NOT_SUBMITTED);
        User persistedUser = user(10L, User.KycStatus.APPROVED);
        when(userRepository.findById(10L)).thenReturn(Optional.of(persistedUser));

        assertDoesNotThrow(() -> kycGuardService.requireApproved(sessionUser));
    }

    @Test
    void blocksTransferUsingLatestDatabaseStatus() {
        User sessionUser = user(11L, User.KycStatus.APPROVED);
        User persistedUser = user(11L, User.KycStatus.PENDING);
        when(userRepository.findById(11L)).thenReturn(Optional.of(persistedUser));

        KycRequiredException exception = assertThrows(
                KycRequiredException.class,
                () -> kycGuardService.requireApproved(sessionUser)
        );

        assertEquals(User.KycStatus.PENDING, exception.getKycStatus());
    }

    private User user(Long id, User.KycStatus status) {
        User user = new User();
        user.setId(id);
        user.setKycStatus(status);
        return user;
    }
}
