package com.example.bank.dto;

import java.util.List;

public record KycStatusResponse(
        String status,
        boolean kycRequired,
        String nextAction,
        String redirectTo,
        List<KycDocumentResponse> documents
) {

    public KycStatusResponse(KycState state, List<KycDocumentResponse> documents) {
        this(
                state.kycStatus(),
                state.kycRequired(),
                state.nextAction(),
                state.redirectTo(),
                documents
        );
    }
}
