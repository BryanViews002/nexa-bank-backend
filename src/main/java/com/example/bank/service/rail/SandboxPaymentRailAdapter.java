package com.example.bank.service.rail;

import com.example.bank.entity.ExternalTransfer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Default adapter used until a real provider contract is in place. It accepts every
 * instruction and returns a reference in the same shape a real processor would, so the
 * settlement path can be exercised end to end by posting a signed webhook.
 */
@Component
public class SandboxPaymentRailAdapter implements PaymentRailAdapter {

    private final String webhookSecret;

    public SandboxPaymentRailAdapter(@Value("${app.payment.webhook-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String name() {
        return "sandbox";
    }

    @Override
    public boolean supports(ExternalTransfer.PaymentRail rail) {
        return true;
    }

    @Override
    public AdapterResult submitPayout(ExternalTransfer transfer) {
        return new AdapterResult(reference("po"), ExternalTransfer.TransferStatus.PROCESSING,
                "Payout accepted by the sandbox provider");
    }

    @Override
    public AdapterResult submitFunding(ExternalTransfer transfer) {
        return new AdapterResult(reference("fi"), ExternalTransfer.TransferStatus.PROCESSING,
                "Funding accepted by the sandbox provider");
    }

    @Override
    public boolean verifySignature(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String provided = signatureHeader.startsWith("sha256=")
                ? signatureHeader.substring("sha256=".length())
                : signatureHeader;
        return MessageDigest.isEqual(
                sign(rawPayload).getBytes(StandardCharsets.UTF_8),
                provided.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    /** Exposed so integration tests and local tooling can produce a valid signature. */
    public String sign(String rawPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute the webhook signature", exception);
        }
    }

    private String reference(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
