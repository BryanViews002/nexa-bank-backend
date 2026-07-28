package com.example.bank.dto;

import com.example.bank.entity.User;

public record KycState(
        String kycStatus,
        boolean kycRequired,
        String nextAction,
        String redirectTo
) {

    public static KycState from(User.KycStatus status) {
        User.KycStatus currentStatus = status == null ? User.KycStatus.NOT_SUBMITTED : status;
        return switch (currentStatus) {
            case NOT_SUBMITTED, REJECTED ->
                    new KycState(currentStatus.name(), true, "COMPLETE_KYC", "/kyc");
            case PENDING ->
                    new KycState(currentStatus.name(), true, "AWAIT_KYC_REVIEW", "/kyc");
            case APPROVED ->
                    new KycState(currentStatus.name(), false, "CONTINUE", "/dashboard");
        };
    }
}
