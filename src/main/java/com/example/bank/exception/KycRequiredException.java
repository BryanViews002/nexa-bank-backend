package com.example.bank.exception;

import com.example.bank.entity.User;
import org.springframework.security.access.AccessDeniedException;

public class KycRequiredException extends AccessDeniedException {

    private final User.KycStatus kycStatus;

    public KycRequiredException(User.KycStatus kycStatus) {
        super("KYC approval is required for this operation. Current status: " + kycStatus);
        this.kycStatus = kycStatus;
    }

    public User.KycStatus getKycStatus() {
        return kycStatus;
    }
}
