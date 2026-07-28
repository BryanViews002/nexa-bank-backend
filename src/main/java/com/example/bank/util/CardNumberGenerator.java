package com.example.bank.util;

import com.example.bank.entity.Card;

import java.security.SecureRandom;

/**
 * Generates Luhn-valid test card numbers. These are issued against the bank's own
 * ledger and are not usable on any real card network.
 */
public final class CardNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CardNumberGenerator() {
    }

    public static String generate(Card.CardBrand brand) {
        StringBuilder digits = new StringBuilder(brand == Card.CardBrand.VISA ? "4" : "5");
        while (digits.length() < 15) {
            digits.append(RANDOM.nextInt(10));
        }
        return digits.append(checkDigit(digits.toString())).toString();
    }

    public static String generateCvv() {
        return String.format("%03d", RANDOM.nextInt(1000));
    }

    private static int checkDigit(String partial) {
        int sum = 0;
        boolean doubling = true;
        for (int i = partial.length() - 1; i >= 0; i--) {
            int digit = partial.charAt(i) - '0';
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubling = !doubling;
        }
        return (10 - (sum % 10)) % 10;
    }
}
