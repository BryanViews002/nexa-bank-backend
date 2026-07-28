package com.example.bank.service.rail;

import com.example.bank.entity.ExternalTransfer;

/**
 * Boundary between the bank and an external money-movement provider. Implement this
 * interface per provider (ACH processor, SEPA gateway, card acquirer) and register it as
 * a Spring bean; {@code PaymentRailService} resolves adapters by {@link #name()}.
 *
 * <p>Adapters must never move money themselves. They submit an instruction and report
 * back what the provider said; the ledger is only touched when a settlement webhook
 * arrives, which keeps the books consistent with the provider's view of the world.
 */
public interface PaymentRailAdapter {

    /** Provider key used in configuration and on the webhook URL. */
    String name();

    boolean supports(ExternalTransfer.PaymentRail rail);

    /** Submits an outbound payout instruction. */
    AdapterResult submitPayout(ExternalTransfer transfer);

    /** Submits an inbound funding (pull) instruction. */
    AdapterResult submitFunding(ExternalTransfer transfer);

    /** Verifies the authenticity of a webhook delivery. */
    boolean verifySignature(String rawPayload, String signatureHeader);

    record AdapterResult(
            String providerReference,
            ExternalTransfer.TransferStatus status,
            String message
    ) {
    }
}
